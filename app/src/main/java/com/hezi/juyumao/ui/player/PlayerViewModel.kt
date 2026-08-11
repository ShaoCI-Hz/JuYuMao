package com.hezi.juyumao.ui.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.repository.MetadataRepository
import com.hezi.juyumao.data.repository.SettingsRepository
import com.hezi.juyumao.player.MusicPlayerService
import com.hezi.juyumao.player.PlaybackController
import com.hezi.juyumao.player.PlaybackStateHolder
import com.hezi.juyumao.player.audio.LyricsData
import com.hezi.juyumao.player.audio.SpectrumAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songDao: SongDao,
    private val metadataRepository: MetadataRepository,
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

    private var loadJob: Job? = null

    init {
        val songId = savedStateHandle.get<Long>("songId")
        if (songId != null && songId > 0) {
            loadSong(songId)
        }
        // 播放统计由 PlaybackController.onMediaItemTransition 统一埋点（T10.9），此处不再重复
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
                _lyrics.value = metadataRepository.getLyrics(enriched)
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
