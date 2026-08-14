package com.hezi.juyumao.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.player.PlaybackStateHolder
import com.hezi.juyumao.player.audio.AudioEffectsManager
import com.hezi.juyumao.player.audio.EqualizerState
import com.hezi.juyumao.player.audio.SpectrumAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val audioEffectsManager: AudioEffectsManager,
    private val spectrumAnalyzer: SpectrumAnalyzer,
    playbackStateHolder: PlaybackStateHolder,
) : ViewModel() {

    val state: StateFlow<EqualizerState> = audioEffectsManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EqualizerState())

    val spectrum: StateFlow<FloatArray> = spectrumAnalyzer.spectrum

    init {
        // 绑定当前播放器会话的频谱（若有）
        viewModelScope.launch {
            val player = playbackStateHolder.getExoPlayer() ?: return@launch
            val sessionId = player.audioSessionId
            if (sessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                spectrumAnalyzer.start(sessionId, enabled = true)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        audioEffectsManager.setEnabled(enabled)
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        audioEffectsManager.setBandLevel(bandIndex, level)
    }

    fun usePreset(presetIndex: Short) {
        audioEffectsManager.usePreset(presetIndex)
    }

    /** 内置预设列表（不依赖设备） */
    val builtinPresets = audioEffectsManager.builtinPresets

    /** 应用内置预设（固定曲线） */
    fun applyBuiltinPreset(name: String) {
        audioEffectsManager.applyBuiltinPreset(name)
    }

    // ── 音效增强（T10.7）──

    fun setBassBoostEnabled(enabled: Boolean) {
        audioEffectsManager.setBassBoostEnabled(enabled)
    }

    fun setBassBoostStrength(strength: Short) {
        audioEffectsManager.setBassBoostStrength(strength)
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        audioEffectsManager.setVirtualizerEnabled(enabled)
    }

    fun setLoudnessGain(gainMb: Int) {
        audioEffectsManager.setLoudnessGain(gainMb)
    }

    override fun onCleared() {
        spectrumAnalyzer.stop()
        super.onCleared()
    }
}
