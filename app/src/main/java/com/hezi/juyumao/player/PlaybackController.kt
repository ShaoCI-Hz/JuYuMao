package com.hezi.juyumao.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.remote.smb.SmbConnectionPool
import com.hezi.juyumao.data.repository.SettingsRepository
import com.hezi.juyumao.domain.model.RepeatMode
import com.hezi.juyumao.domain.model.Song
import com.hezi.juyumao.domain.model.SongSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val playbackStateHolder: PlaybackStateHolder,
    private val smbConnectionPool: SmbConnectionPool,
    private val serverDao: ServerDao,
    private val songDao: SongDao,
    private val dynamicLoadControl: DynamicLoadControl,
    private val settingsRepository: SettingsRepository,
) {
    private val queue = PlaybackQueue()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 本地文件 MediaSource 工厂（构建整队列时用） */
    private val localSourceFactory = DefaultMediaSourceFactory(context)

    /** 最近一次播放错误的当前歌曲 id，用于防死循环（连续同一首歌失败则不再自动跳过） */
    private var lastErrorSongId: Long? = null

    /** 淡入淡出任务（防止并发交叉） */
    private var fadeJob: Job? = null

    /** 无缝播放开关（缓存设置值） */
    @Volatile private var gaplessEnabled: Boolean = false

    /** 交叉淡化时长 ms（0 = 关闭） */
    @Volatile private var crossfadeMs: Int = 0

    init {
        // 解码失败兜底：提示 + 自动跳过下一首（T9.5）
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val song = queue.currentSong()
                val songId = song?.id
                // 按错误类型给提示：网络/IO 类可重试，解码类提示格式不支持
                val message = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    -> "网络中断，请检查 NAS 连接"
                    else -> "无法播放该格式"
                }
                playbackStateHolder.setErrorMessage("$message，已跳过")
                if (songId != null && songId != lastErrorSongId) {
                    lastErrorSongId = songId
                    scope.launch {
                        // 无缝模式错误后 ExoPlayer 停在 STATE_IDLE，必须先 prepare 才能离开；
                        // 否则 seek/play 无效，无缝队列永久卡死
                        if (exoPlayer.mediaItemCount > 1) {
                            exoPlayer.prepare()
                            exoPlayer.seekToDefaultPosition(queue.currentIndex.value)
                            exoPlayer.play()
                        } else {
                            playCurrent()
                        }
                    }
                } else {
                    // 连续同一首失败：停止，避免死循环
                    lastErrorSongId = null
                    playbackStateHolder.updatePlaying(false)
                }
            }
        })

        // 无缝/淡化设置持续生效（改设置后无需杀进程）；倍速仍只读一次启动值
        scope.launch {
            try { settingsRepository.gaplessPlayback.collect { gaplessEnabled = it } } catch (_: Exception) {}
        }
        scope.launch {
            try { settingsRepository.crossfadeDuration.collect { crossfadeMs = it } } catch (_: Exception) {}
        }
        scope.launch {
            val speed = try { settingsRepository.playbackSpeed.first() } catch (_: Exception) { 1.0f }
            if (speed > 0f && speed != 1.0f) exoPlayer.setPlaybackSpeed(speed)
        }

        // 播放统计埋点：曲目切换时递增 playCount（T10.9）
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // 无缝模式自然切歌：先同步队列索引，保证统计与 currentSong 一致（不记旧歌）
                val mediaIndex = exoPlayer.currentMediaItemIndex
                if (mediaIndex in 0 until queue.songs.value.size) queue.syncIndex(mediaIndex)
                val song = queue.currentSong() ?: return
                scope.launch {
                    try { songDao.incrementPlayCount(song.id) } catch (_: Exception) {}
                }
            }
        })
    }

    /** 当前播放模式：0=OFF, 1=ALL, 2=ONE */
    private var repeatModeIndex: Int = 0

    /** 是否随机 */
    private var shuffleEnabled: Boolean = false

    private val repeatMode: RepeatMode
        get() = when (repeatModeIndex) {
            1 -> RepeatMode.ALL
            2 -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

    // ── 公开接口 ──

    fun loadPlaylist(songs: List<SongEntity>, startIndex: Int = 0) {
        val domainSongs = songs.map { it.toDomain() }
        queue.setQueue(domainSongs, startIndex)
        scope.launch {
            // 无缝播放开启时整队列加载，由 ExoPlayer 衔接曲目
            if (gaplessEnabled && songs.size > 1) {
                // 无缝队列按整库 HiRes 情况设置缓冲档位（单个 playCurrent 只在单曲模式调用）
                val bufferKb = try { settingsRepository.audioBufferSize.first() } catch (_: Exception) { 256 }
                dynamicLoadControl.updateSettings(bufferKb, songs.any { it.isHiRes })
                val sources = buildMediaSources(songs)
                exoPlayer.setMediaSources(sources, startIndex.coerceIn(0, sources.size - 1), 0L)
                syncExoPlayerRepeatMode()
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                playCurrent()
            }
        }
    }

    fun play() {
        // 交叉淡化关闭时保证音量归一（避免上次淡出/取消停在中间音量）
        if (crossfadeMs <= 0) exoPlayer.setVolume(1f)
        exoPlayer.playWhenReady = true
        // 淡入（交叉淡化开启时）
        if (crossfadeMs > 0) {
            fadeJob?.cancel()
            fadeJob = scope.launch { fadeVolume(from = 0f, to = 1f) }
        }
        // isPlaying 由 ExoPlayer 的 onIsPlayingChanged 回调同步，不在此处虚报
    }

    fun pause() {
        // 淡出后暂停（交叉淡化开启时）
        if (crossfadeMs > 0 && exoPlayer.isPlaying) {
            fadeJob?.cancel()
            fadeJob = scope.launch {
                fadeVolume(from = 1f, to = 0f)
                exoPlayer.playWhenReady = false
            }
        } else {
            exoPlayer.playWhenReady = false
        }
        playbackStateHolder.updatePlaying(false)
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    fun next() {
        if (queue.next(repeatMode, shuffleEnabled) == null) return
        scope.launch {
            if (gaplessEnabled && exoPlayer.mediaItemCount > 1) {
                // 无缝模式：ExoPlayer 已加载整队列，直接跳转
                exoPlayer.seekToDefaultPosition(queue.currentIndex.value)
                exoPlayer.play()
            } else {
                playCurrent()
            }
        }
    }

    fun previous() {
        // shuffle 模式下上一首也走随机序列（与 next 对称）
        if (queue.previous(repeatMode, shuffleEnabled) == null) return
        scope.launch {
            if (gaplessEnabled && exoPlayer.mediaItemCount > 1) {
                exoPlayer.seekToDefaultPosition(queue.currentIndex.value)
                exoPlayer.play()
            } else {
                playCurrent()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackStateHolder.seekTo(positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
    }

    fun setRepeat(modeIndex: Int) {
        repeatModeIndex = modeIndex
        // 无缝模式由 ExoPlayer 承担列表/单曲循环（非无缝走 queue 逻辑，此处同步无副作用）
        exoPlayer.repeatMode = when (modeIndex) {
            2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
            1 -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    /** 无缝模式下按当前模式同步 ExoPlayer 循环模式 */
    private fun syncExoPlayerRepeatMode() {
        exoPlayer.repeatMode = when (repeatModeIndex) {
            2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
            1 -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    fun currentSong(): Song? = queue.currentSong()
        ?: playbackStateHolder.currentSong.value?.toDomain()

    fun getQueue(): List<Song> = queue.songs.value

    fun getQueueIndex(): Int = queue.currentIndex.value

    /** 响应式队列（QueueViewModel 订阅，播放中变化自动反映） */
    fun queueSongs(): StateFlow<List<Song>> = queue.songs

    /** 响应式当前索引 */
    fun queueIndex(): StateFlow<Int> = queue.currentIndex

    fun playAt(index: Int) {
        queue.playAt(index)
        scope.launch {
            if (gaplessEnabled && exoPlayer.mediaItemCount > 1) {
                exoPlayer.seekToDefaultPosition(queue.currentIndex.value)
                exoPlayer.play()
            } else {
                playCurrent()
            }
        }
    }

    fun clearQueue() {
        queue.clear()
        fadeJob?.cancel()
        exoPlayer.stop()
        // Media3 的 stop 保留队列，需显式清空，否则下次 play() 会重播残留队列
        exoPlayer.setMediaItems(emptyList())
        playbackStateHolder.updateSong(null)
    }

    /** 设置倍速并持久化（倍速下音高不变，Media3 内置支持） */
    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 2.0f)
        exoPlayer.setPlaybackSpeed(safe)
        scope.launch {
            try { settingsRepository.setPlaybackSpeed(safe) } catch (_: Exception) {}
        }
    }

    // ── 内部 ──

    /** 为整队列构建 MediaSource（SMB 歌曲逐首连接，失败降级为本地占位源） */
    private suspend fun buildMediaSources(songs: List<SongEntity>): List<MediaSource> {
        val sources = mutableListOf<MediaSource>()
        for (song in songs) {
            val domain = song.toDomain()
            // 每首歌用自己的封面（不再全局复用 artworkUri，避免切歌后通知栏/媒体控件封面错误）
            val artPath = song.albumArtUri ?: playbackStateHolder.artworkUri.value
            val metadata = MediaMetadata.Builder()
                .setTitle(domain.title)
                .setArtist(domain.artist)
                .setAlbumTitle(domain.album)
                .also { builder ->
                    if (artPath != null) builder.setArtworkUri(Uri.parse("file://$artPath"))
                }
                .build()

            if (domain.source == SongSource.SMB && song.smbServerId != null && song.smbSharePath != null) {
                try {
                    val server = serverDao.getServerById(song.smbServerId)?.decryptPassword()
                    if (server != null) {
                        val smbClient = smbConnectionPool.getConnection(
                            serverId = server.id,
                            host = server.ip,
                            port = server.port,
                            username = server.username,
                            password = server.password,
                            shareName = server.effectiveShareName,
                        )
                        sources.add(createSmbMediaSource(smbClient, song.smbSharePath, song.mimeType, metadata))
                        continue
                    }
                } catch (_: Exception) {}
                // SMB 连接失败：降级为本地占位源（保持 sources 与 queue 索引对齐），播放时由 onPlayerError 跳过
                sources.add(
                    localSourceFactory.createMediaSource(
                        MediaItem.Builder()
                            .setUri(song.smbSharePath)
                            .setMediaMetadata(metadata)
                            .build()
                    )
                )
            } else {
                sources.add(
                    localSourceFactory.createMediaSource(
                        MediaItem.Builder()
                            .setUri(domain.filePath)
                            .setMediaMetadata(metadata)
                            .build()
                    )
                )
            }
        }
        return sources
    }

    private suspend fun playCurrent() {
        val song = queue.currentSong() ?: return
        // 非淡入淡出路径确保音量归一（上次淡出取消可能停在中间值）
        if (crossfadeMs <= 0) exoPlayer.setVolume(1f)
        // 动态缓冲：HiRes 歌曲用更大预缓冲（SMB 大文件防卡顿）
        val bufferKb = try { settingsRepository.audioBufferSize.first() } catch (_: Exception) { 256 }
        dynamicLoadControl.updateSettings(bufferKb, song.isHiRes)
        // 每首歌用自己的封面
        val artPath = song.albumArtUri ?: playbackStateHolder.artworkUri.value

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .also { builder ->
                if (artPath != null) {
                    builder.setArtworkUri(Uri.parse("file://$artPath"))
                }
            }
            .build()

        if (song.source == SongSource.SMB && song.smbServerId != null && song.smbSharePath != null) {
            // SMB 歌曲：使用 SmbMediaSource（metadata 在 MediaSource 内，避免 setMediaItem 被覆盖）
            try {
                val server = serverDao.getServerById(song.smbServerId)?.decryptPassword()
                if (server != null) {
                    val smbClient = smbConnectionPool.getConnection(
                        serverId = server.id,
                        host = server.ip,
                        port = server.port,
                        username = server.username,
                        password = server.password,
                        shareName = server.effectiveShareName,
                    )
                    val mediaSource = createSmbMediaSource(smbClient, song.smbSharePath, song.mimeType, metadata)
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return
                }
            } catch (_: Exception) {
                // SMB 连接失败，跳过
            }
        }

        // 本地歌曲或 SMB 失败回退
        val mediaItem = MediaItem.Builder()
            .setUri(song.filePath)
            .setMediaMetadata(metadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** 音量渐变（淡入淡出/交叉淡化，Media3 1.5.1 无 AudioFade API，手动实现） */
    private suspend fun fadeVolume(from: Float, to: Float) {
        if (crossfadeMs <= 0) return
        val steps = 20
        val stepMs = crossfadeMs / steps
        try {
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val volume = from + (to - from) * progress
                try { exoPlayer.setVolume(volume.coerceIn(0f, 1f)) } catch (_: Exception) {}
                delay(stepMs.toLong())
            }
        } finally {
            // 任务被取消时把音量收敛到目标端，避免停在中间音量（下次播放静音/错乱）
            try { exoPlayer.setVolume(to.coerceIn(0f, 1f)) } catch (_: Exception) {}
        }
    }
}

/** SongEntity -> Song 领域模型转换 */
fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtUri = albumArtUri,
    duration = duration,
    filePath = filePath,
    fileSize = fileSize,
    mimeType = mimeType,
    isHiRes = isHiRes,
    source = if (source == "SMB") SongSource.SMB else SongSource.LOCAL,
    smbServerId = smbServerId,
    smbSharePath = smbSharePath,
    sampleRate = sampleRate,
    bitsPerSample = bitsPerSample,
    bitrate = bitrate,
)
