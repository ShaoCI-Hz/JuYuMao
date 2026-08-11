package com.hezi.juyumao.player.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBand(
    val index: Short,
    val centerFreq: Int,      // in milliHz
    val minLevel: Short,
    val maxLevel: Short,
    val currentLevel: Short,
)

data class EqualizerPreset(
    val index: Short,
    val name: String,
)

data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<EqualizerPreset> = emptyList(),
    val currentPreset: Short = -1, // -1 = custom
    // 音效增强（T10.7）
    val bassBoostAvailable: Boolean = false,
    val bassBoostStrength: Short = 0, // 0-1000
    val bassBoostEnabled: Boolean = false,
    val virtualizerAvailable: Boolean = false,
    val virtualizerEnabled: Boolean = false,
    val loudnessAvailable: Boolean = false,
    val loudnessGain: Int = 0, // milliBels，0 = 关闭
)

@Singleton
class AudioEffectsManager @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachToPlayer(exoPlayer: ExoPlayer) {
        release()
        // audioSessionId 在 prepare 后才有效；先监听 ready 再绑定
        val attach = {
            try {
                val sessionId = exoPlayer.audioSessionId
                equalizer = Equalizer(0, sessionId)
                bassBoost = try { BassBoost(0, sessionId) } catch (_: Exception) { null }
                virtualizer = try { Virtualizer(0, sessionId) } catch (_: Exception) { null }
                loudness = try { LoudnessEnhancer(sessionId) } catch (_: Exception) { null }
                refreshState()
            } catch (_: Exception) {
                // Equalizer not available on this device
            }
        }
        if (exoPlayer.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            attach()
        } else {
            exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        exoPlayer.removeListener(this)
                        attach()
                    }
                }
            })
        }
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, level)
            // 手动调频后进入自定义模式：重置 currentPreset，
            // 否则 Android Equalizer.currentPreset 仍返回旧预设，UI 同时显示"已选预设"与自定义频段
            _state.value = _state.value.copy(currentPreset = -1)
            refreshState()
        } catch (_: Exception) {}
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            _state.value = _state.value.copy(currentPreset = presetIndex)
            refreshState()
        } catch (_: Exception) {}
    }

    // ── 音效增强（T10.7）──

    fun setBassBoostEnabled(enabled: Boolean) {
        try {
            bassBoost?.enabled = enabled
            refreshState()
        } catch (_: Exception) {}
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            val s = strength.coerceIn(0, 1000)
            bassBoost?.setStrength(s)
            bassBoost?.enabled = s > 0
            refreshState()
        } catch (_: Exception) {}
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        try {
            virtualizer?.enabled = enabled
            refreshState()
        } catch (_: Exception) {}
    }

    fun setLoudnessGain(gainMb: Int) {
        try {
            loudness?.setTargetGain(gainMb)
            loudness?.enabled = gainMb != 0
            refreshState()
        } catch (_: Exception) {}
    }
    private fun refreshState() {
        val eq = equalizer
        val bands = if (eq != null) {
            try {
                (0 until eq.numberOfBands.toInt()).map { i ->
                    val bandRange = eq.bandLevelRange
                    EqualizerBand(
                        index = i.toShort(),
                        centerFreq = eq.getCenterFreq(i.toShort()),
                        minLevel = bandRange[0],
                        maxLevel = bandRange[1],
                        currentLevel = eq.getBandLevel(i.toShort()),
                    )
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val presets = if (eq != null) {
            try {
                (0 until eq.numberOfPresets.toInt()).map { i ->
                    EqualizerPreset(
                        index = i.toShort(),
                        name = eq.getPresetName(i.toShort()),
                    )
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val bb = bassBoost
        val vir = virtualizer
        val loud = loudness
        val loudGain: Int = if (loud != null) {
            try { loud.getTargetGain().toInt() } catch (_: Exception) { 0 }
        } else 0
        _state.value = _state.value.copy(
            enabled = try { eq?.enabled ?: false } catch (_: Exception) { false },
            bands = bands,
            presets = presets,
            currentPreset = try { eq?.currentPreset ?: -1 } catch (_: Exception) { -1 },
            bassBoostAvailable = bb != null,
            bassBoostStrength = try { bb?.getRoundedStrength() ?: 0 } catch (_: Exception) { 0 },
            bassBoostEnabled = try { bb?.enabled ?: false } catch (_: Exception) { false },
            virtualizerAvailable = vir != null,
            virtualizerEnabled = try { vir?.enabled ?: false } catch (_: Exception) { false },
            loudnessAvailable = loud != null,
            loudnessGain = loudGain,
        )
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
    }
}
