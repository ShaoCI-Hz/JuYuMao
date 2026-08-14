package com.hezi.juyumao.ui.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.lyrics.LyricsManager
import com.hezi.juyumao.data.repository.MetadataRepository
import com.hezi.juyumao.data.repository.SettingsRepository
import com.hezi.juyumao.player.MusicPlayerService
import com.hezi.juyumao.player.PlaybackController
import com.hezi.juyumao.player.PlaybackStateHolder
import com.hezi.juyumao.player.audio.LyricsData
import com.hezi.juyumao.player.audio.SpectrumAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.updateAll
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songDao: SongDao,
    private val metadataRepository: MetadataRepository,
    private val lyricsManager: LyricsManager,
    private val playbackStateHolder: PlaybackStateHolder,
    private val playbackController: PlaybackController,
    private val settingsRepository: SettingsRepository,
    private val spectrumAnalyzer: SpectrumAnalyzer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong

    private val _artworkUri = MutableStateFlow<String?>(null)
    val artworkUri: StateFlow<String?> = _artworkUri

    private val _lyrics = MutableStateFlow<LyricsData?>(null)
    val lyrics: StateFlow<LyricsData?> = _lyrics

    /** 当前歌曲收藏状态（持久化到数据库） */
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    // 直接从 PlaybackStateHolder 读取，由其内部轮询驱动更新
    val isPlaying: StateFlow<Boolean> = playbackStateHolder.isPlaying
    val position: StateFlow<Long> = playbackStateHolder.position
    val duration: StateFlow<Long> = playbackStateHolder.duration

    val lyricsFontSize: StateFlow<Float> = settingsRepository.lyricsFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)
    val lyricsFontBold: StateFlow<Boolean> = settingsRepository.lyricsFontBold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 频谱可视化开关（T11.4） */
    val spectrumEnabled: StateFlow<Boolean> = settingsRepository.spectrumVisualizer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 频谱数据（由 SpectrumAnalyzer 采集） */
    val spectrum: StateFlow<FloatArray> = spectrumAnalyzer.spectrum

    /** 在线歌词开关（P1-7） */
    private var onlineLyricsEnabled: Boolean = true

    /** 通知歌词行更新任务（P1-12） */
    private var lyricsLineJob: Job? = null

    private var loadJob: Job? = null

    /** 取当前播放位置对应的歌词行（无时间戳或空返回 null） */
    private fun currentLyricLine(data: com.hezi.juyumao.player.audio.LyricsData?, positionMs: Long): String? {
        if (data == null || data.lines.isEmpty()) return null
        if (!data.lines.any { it.timeMs > 0 }) return null
        val idx = com.hezi.juyumao.player.audio.LrcParser.findCurrentLineIndex(data.lines, positionMs)
        return if (idx >= 0) data.lines[idx].text else null
    }

    init {
        // 在线歌词开关（P1-7）
        viewModelScope.launch {
            try { onlineLyricsEnabled = settingsRepository.onlineLyrics.first() } catch (_: Exception) {}
        }
        // 播放状态变化刷新桌面小组件（P1-13）
        viewModelScope.launch {
            try {
                playbackStateHolder.isPlaying.collect {
                    com.hezi.juyumao.ui.widget.JuYuMaoWidget().updateAll(context)
                }
            } catch (_: Exception) {}
        }
        val songId = savedStateHandle.get<Long>("songId")
        if (songId != null && songId > 0) {
            loadSong(songId)
        }
        // 播放统计由 PlaybackController.onMediaItemTransition 统一埋点（T10.9），此处不再重复

        // A-B 循环监控（P2-12）：播放到 B 点自动跳回 A 点
        viewModelScope.launch {
            combine(
                playbackController.abLoop,
                playbackStateHolder.position,
                playbackStateHolder.isPlaying,
            ) { loop, pos, playing -> Triple(loop, pos, playing) }
                .collect { (loop, pos, playing) ->
                    if (loop != null && playing && pos >= loop.second) {
                        playbackController.seekTo(loop.first)
                    }
                }
        }
    }

    // ── A-B 循环（P2-12）──

    val abLoop: StateFlow<Pair<Long, Long>?> = playbackController.abLoop

    /** 设置 A-B 循环区间 */
    fun setAbLoop(startMs: Long, endMs: Long) {
        playbackController.setAbLoop(startMs, endMs)
    }

    /** 清除 A-B 循环 */
    fun clearAbLoop() {
        playbackController.clearAbLoop()
    }

    fun loadSong(songId: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 先清空旧状态，避免加载失败/无结果时残留上一首歌的歌词/封面/收藏
            _lyrics.value = null
            _artworkUri.value = null
            _isFavorite.value = false

            var song = songDao.getById(songId) ?: return@launch
            _isFavorite.value = song.isFavorite

            // 判断是否已在播同一首：以 PlaybackController 真实队列为准（切歌后 playbackStateHolder.currentSong 可能滞后）
            val alreadyPlayingThis = playbackController.currentSong()?.id == songId &&
                (playbackStateHolder.getExoPlayer()?.mediaItemCount ?: 0) > 0

            // 提取完整元数据（歌手/专辑/封面/内嵌歌词标志），SMB 歌曲会下载头部标签
            val enriched = metadataRepository.extractAndUpdateSong(song)
            if (enriched != song) {
                song = enriched
                // 统一在这里写 lastPlayedAt（前置写入会被 extractAndUpdateSong 的旧对象 update 覆盖）
                val now = System.currentTimeMillis()
                songDao.update(enriched.copy(lastPlayedAt = now))
                _currentSong.value = enriched
                playbackStateHolder.updateSong(enriched)
            } else {
                if (!alreadyPlayingThis) {
                    songDao.update(song.copy(lastPlayedAt = System.currentTimeMillis()))
                }
                _currentSong.value = song
                playbackStateHolder.updateSong(song)
            }

            // 封面
            val artPath = enriched.albumArtUri ?: metadataRepository.getCachedArtworkPath(enriched.id)
            _artworkUri.value = artPath
            playbackStateHolder.updateArtwork(artPath)

            // 歌词
            try {
                var loadedLyrics = metadataRepository.getLyrics(enriched)
                // 在线歌词兜底（P1-7）：本地无歌词且开关开启时在线获取并缓存
                if (loadedLyrics == null && onlineLyricsEnabled) {
                    loadedLyrics = lyricsManager.fetchOnlineLyrics(enriched)
                }
                _lyrics.value = loadedLyrics
                // 通知栏当前歌词行（P1-12）
                playbackStateHolder.updateLyricsLine(
                    currentLyricLine(loadedLyrics, playbackStateHolder.position.value)
                )
                lyricsLineJob?.cancel()
                if (loadedLyrics != null && loadedLyrics.lines.any { it.timeMs > 0 }) {
                    lyricsLineJob = viewModelScope.launch {
                        var lastLine: String? = null
                        while (true) {
                            delay(500)
                            val line = currentLyricLine(loadedLyrics, playbackStateHolder.position.value)
                            if (line != lastLine) {
                                playbackStateHolder.updateLyricsLine(line)
                                lastLine = line
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 只有不是已加载的同一首歌时才重新加载播放，否则保持当前播放状态
            if (!alreadyPlayingThis) {
                playbackController.loadPlaylist(listOf(enriched), 0)

                // 频谱采集：等待 audioSessionId 就绪后绑定（并入 loadJob，随取消一起回收）
                val enabled = try { settingsRepository.spectrumVisualizer.first() } catch (_: Exception) { true }
                val player = playbackStateHolder.getExoPlayer()
                if (player != null) {
                    // prepare 后 audioSessionId 才有效，轮询等待
                    repeat(50) {
                        if (player.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                            spectrumAnalyzer.start(player.audioSessionId, enabled)
                            return@launch
                        }
                        delay(100)
                    }
                }

                // 启动通知栏服务
                try {
                    val intent = Intent(context, MusicPlayerService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun togglePlay() {
        playbackController.togglePlay()
    }

    fun next() {
        playbackController.next()
        // 控制器切歌后同步 UI（标题/封面/歌词/收藏），不重播（loadSong 内 alreadyPlayingThis 判定）
        val newId = playbackController.currentSong()?.id
        if (newId != null && newId != _currentSong.value?.id) loadSong(newId)
    }

    /** 更新当前歌曲星级评分 */
    fun setRating(rating: Int) {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            songDao.updateRating(song.id, rating)
        }
    }

    /** 播放相似歌曲（P1-9：同艺术家或同流派，按播放次数排序） */
    fun playSimilar() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            try {
                val similar = songDao.getSimilarSongs(song.id, song.artist, song.genre, 20)
                if (similar.isNotEmpty()) playbackController.loadPlaylist(similar, 0)
            } catch (_: Exception) {}
        }
    }

    /** 编辑标签结果提示（一次性） */
    private val _editMessage = MutableStateFlow<String?>(null)
    val editMessage: StateFlow<String?> = _editMessage

    fun consumeEditMessage() {
        _editMessage.value = null
    }

    /** 编辑本地歌曲标签（写文件 + 更新数据库） */
    fun editTags(title: String, artist: String, album: String, genre: String, year: Int) {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            if (song.source != "LOCAL") {
                _editMessage.value = "仅本地歌曲可编辑标签"
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                com.hezi.juyumao.data.local.metadata.TagEditor.editLocalTags(
                    java.io.File(song.filePath), title, artist, album, genre, year)
            }
            result.fold(
                onSuccess = {
                    songDao.updateTags(song.id, title, artist, album, genre, year)
                    _currentSong.value = song.copy(title = title, artist = artist, album = album, genre = genre, year = year)
                    _editMessage.value = "标签已更新"
                },
                onFailure = { _editMessage.value = "编辑失败: ${it.message}" },
            )
        }
    }

    fun previous() {
        playbackController.previous()
        val newId = playbackController.currentSong()?.id
        if (newId != null && newId != _currentSong.value?.id) loadSong(newId)
    }

    fun setShuffle(enabled: Boolean) {
        playbackController.setShuffle(enabled)
    }

    fun setRepeat(modeIndex: Int) {
        playbackController.setRepeat(modeIndex)
    }

    /** 切换收藏状态并持久化（列表/播放页/我喜欢三处共用） */
    fun toggleFavorite() {
        val songId = _currentSong.value?.id ?: return
        // 乐观更新：先取反再写库，避免连点竞态读到旧值导致两次写同一值
        _isFavorite.value = !_isFavorite.value
        viewModelScope.launch {
            try {
                songDao.updateFavorite(songId, _isFavorite.value)
            } catch (_: Exception) {
                // 写库失败回滚乐观值
                _isFavorite.value = !_isFavorite.value
            }
        }
    }

    /** 设置播放倍速（持久化，重启保留） */
    fun setPlaybackSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
    }

    override fun onCleared() {
        spectrumAnalyzer.stop()
        super.onCleared()
    }
}
