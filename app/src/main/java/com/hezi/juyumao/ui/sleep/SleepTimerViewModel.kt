package com.hezi.juyumao.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SleepTimerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    /** 当前模式：false=倒计时，true=播完当前曲停止 */
    private val _endOfSongMode = MutableStateFlow(false)
    val endOfSongMode: StateFlow<Boolean> = _endOfSongMode

    private var timerJob: Job? = null

    /** 淡出任务（取消定时时一并撤销，避免已暂停） */
    private var fadeJob: Job? = null

    val isTimerRunning: Boolean get() = timerJob?.isActive == true && (_remainingSeconds.value > 0 || _endOfSongMode.value)

    fun setTimer(minutes: Int) {
        cancelTimer()
        if (minutes <= 0) {
            // 播完当前曲后停止（P1-11）
            _endOfSongMode.value = true
            timerJob = viewModelScope.launch {
                var firstSongId: Long? = playbackController.currentSong()?.id
                while (true) {
                    delay(1000)
                    val currentId = playbackController.currentSong()?.id
                    // 队列初始为空时：记录首次播放的歌曲，播完它再停
                    if (firstSongId == null && currentId != null) {
                        firstSongId = currentId
                        continue
                    }
                    if (firstSongId != null && currentId != null && currentId != firstSongId) {
                        playbackController.pause()
                        cancelTimer()
                        return@launch
                    }
                    // 播放停止（用户手动暂停等）也退出
                    if (!playbackController.isPlayingNow()) {
                        cancelTimer()
                        return@launch
                    }
                }
            }
        } else {
            _remainingSeconds.value = minutes * 60
            timerJob = viewModelScope.launch {
                var fading = false
                while (_remainingSeconds.value > 0) {
                    delay(1000)
                    _remainingSeconds.value--
                    // 最后 30 秒淡出音量后暂停（P1-11）
                    if (!fading && _remainingSeconds.value <= 30) {
                        fading = true
                        fadeJob = playbackController.fadeOutAndPause()
                    }
                }
                if (!fading) fadeJob = playbackController.fadeOutAndPause()
                _remainingSeconds.value = 0
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        fadeJob?.cancel()
        fadeJob = null
        _remainingSeconds.value = 0
        _endOfSongMode.value = false
    }
}
