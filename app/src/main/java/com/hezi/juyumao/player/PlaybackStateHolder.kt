package com.hezi.juyumao.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hezi.juyumao.data.local.db.entity.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateHolder @Inject constructor() {

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong

    private val _artworkUri = MutableStateFlow<String?>(null)
    val artworkUri: StateFlow<String?> = _artworkUri

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    /** 播放错误消息（解码失败等），消费后应清除 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    @Volatile private var exoPlayer: ExoPlayer? = null
    private var boundListener: Player.Listener? = null

    // 进度轮询协程
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollJob: Job? = null

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    fun bindPlayer(player: ExoPlayer) {
        // 重复 bind 时先移除旧 listener，避免监听器堆积与旧播放器回调串台
        boundListener?.let { exoPlayer?.removeListener(it) }
        exoPlayer = player
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0)
                } else if (state == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    stopPolling()
                } else if (state == Player.STATE_IDLE) {
                    _isPlaying.value = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                // 播放时启动轮询，暂停时停止
                if (playing) startPolling() else stopPolling()
            }
        }
        boundListener = listener
        player.addListener(listener)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                exoPlayer?.let {
                    if (it.isPlaying) {
                        _position.value = it.currentPosition
                        _duration.value = it.duration.coerceAtLeast(0)
                    }
                }
                delay(200) // 每 200ms 更新一次，流畅不卡
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        // 暂停时最后一次同步位置（播放器已释放时 currentPosition 可能抛异常，需防护）
        exoPlayer?.let {
            try {
                _position.value = it.currentPosition
            } catch (_: Exception) {}
        }
    }

    fun updateSong(song: SongEntity?) {
        _currentSong.value = song
    }

    fun updateArtwork(uri: String?) {
        _artworkUri.value = uri
    }

    fun updatePlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.let {
            it.seekTo(positionMs)
            _position.value = positionMs
        }
    }

    /** 手动同步一次位置（供外部调用） */
    fun syncPosition() {
        exoPlayer?.let {
            _position.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0)
        }
    }

    fun release() {
        pollJob?.cancel()
        scope.cancel()
        // 置空引用：release 后 getExoPlayer() 不应返回已释放实例，避免外部 IllegalStateException
        exoPlayer = null
        boundListener = null
    }
}
