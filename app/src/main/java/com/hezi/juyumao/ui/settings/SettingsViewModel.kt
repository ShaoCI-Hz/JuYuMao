package com.hezi.juyumao.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.repository.SettingsRepository
import com.hezi.juyumao.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val lyricsFontSize: StateFlow<Float> = settingsRepository.lyricsFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)

    val lyricsFontBold: StateFlow<Boolean> = settingsRepository.lyricsFontBold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val onlineLyrics: StateFlow<Boolean> = settingsRepository.onlineLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val replayGain: StateFlow<Boolean> = settingsRepository.replayGain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val accentColor: StateFlow<String> = settingsRepository.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val cacheThreads: StateFlow<Int> = settingsRepository.cacheThreads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val audioBufferSize: StateFlow<Int> = settingsRepository.audioBufferSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 256)

    val gaplessPlayback: StateFlow<Boolean> = settingsRepository.gaplessPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val crossfadeDuration: StateFlow<Int> = settingsRepository.crossfadeDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val spectrumVisualizer: StateFlow<Boolean> = settingsRepository.spectrumVisualizer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val smbAutoConnect: StateFlow<Boolean> = settingsRepository.smbAutoConnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setLyricsFontSize(size: Float) {
        viewModelScope.launch { settingsRepository.setLyricsFontSize(size) }
    }

    fun setLyricsFontBold(bold: Boolean) {
        viewModelScope.launch { settingsRepository.setLyricsFontBold(bold) }
    }

    /** 在线歌词开关（P1-7） */
    fun setOnlineLyrics(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOnlineLyrics(enabled) }
    }

    /** ReplayGain 响度归一化开关（P1-8） */
    fun setReplayGain(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReplayGain(enabled) }
    }

    /** 自定义强调色（P1-14） */
    fun setAccentColor(hex: String) {
        viewModelScope.launch { settingsRepository.setAccentColor(hex) }
    }

    fun setCacheThreads(threads: Int) {
        viewModelScope.launch { settingsRepository.setCacheThreads(threads) }
    }

    fun setAudioBufferSize(kb: Int) {
        viewModelScope.launch { settingsRepository.setAudioBufferSize(kb) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGaplessPlayback(enabled) }
    }

    fun setCrossfadeDuration(ms: Int) {
        viewModelScope.launch { settingsRepository.setCrossfadeDuration(ms) }
    }

    fun setSpectrumVisualizer(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSpectrumVisualizer(enabled) }
    }

    fun setSmbAutoConnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSmbAutoConnect(enabled) }
    }
}
