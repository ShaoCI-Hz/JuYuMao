package com.hezi.juyumao.data.repository

import com.hezi.juyumao.data.local.datastore.SettingsDataStore
import com.hezi.juyumao.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) {
    val themeMode: Flow<ThemeMode> = settingsDataStore.themeMode.map {
        when (it) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    val smbAutoConnect: Flow<Boolean> = settingsDataStore.smbAutoConnect
    val smbConnectionTimeout: Flow<Int> = settingsDataStore.smbConnectionTimeout
    val audioBufferSize: Flow<Int> = settingsDataStore.audioBufferSize
    val gaplessPlayback: Flow<Boolean> = settingsDataStore.gaplessPlayback
    val lyricsFontSize: Flow<Float> = settingsDataStore.lyricsFontSize
    val lyricsFontBold: Flow<Boolean> = settingsDataStore.lyricsFontBold
    val cacheThreads: Flow<Int> = settingsDataStore.cacheThreads
    val playbackSpeed: Flow<Float> = settingsDataStore.playbackSpeed
    val crossfadeDuration: Flow<Int> = settingsDataStore.crossfadeDuration
    val spectrumVisualizer: Flow<Boolean> = settingsDataStore.spectrumVisualizer
    val onlineLyrics: Flow<Boolean> = settingsDataStore.onlineLyrics
    val replayGain: Flow<Boolean> = settingsDataStore.replayGain
    val accentColor: Flow<String> = settingsDataStore.accentColor
    val onboardingCompleted: Flow<Boolean> = settingsDataStore.onboardingCompleted

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDataStore.setThemeMode(
            when (mode) {
                ThemeMode.DARK -> "dark"
                ThemeMode.LIGHT -> "light"
                ThemeMode.SYSTEM -> "system"
            }
        )
    }

    suspend fun setSmbAutoConnect(enabled: Boolean) = settingsDataStore.setSmbAutoConnect(enabled)
    suspend fun setSmbConnectionTimeout(seconds: Int) = settingsDataStore.setSmbConnectionTimeout(seconds)
    suspend fun setAudioBufferSize(kb: Int) = settingsDataStore.setAudioBufferSize(kb)
    suspend fun setGaplessPlayback(enabled: Boolean) = settingsDataStore.setGaplessPlayback(enabled)
    suspend fun setLyricsFontSize(size: Float) = settingsDataStore.setLyricsFontSize(size)
    suspend fun setLyricsFontBold(bold: Boolean) = settingsDataStore.setLyricsFontBold(bold)
    suspend fun setCacheThreads(threads: Int) = settingsDataStore.setCacheThreads(threads)
    suspend fun setPlaybackSpeed(speed: Float) = settingsDataStore.setPlaybackSpeed(speed)
    suspend fun setCrossfadeDuration(ms: Int) = settingsDataStore.setCrossfadeDuration(ms)
    suspend fun setSpectrumVisualizer(enabled: Boolean) = settingsDataStore.setSpectrumVisualizer(enabled)
    suspend fun setOnlineLyrics(enabled: Boolean) = settingsDataStore.setOnlineLyrics(enabled)
    suspend fun setReplayGain(enabled: Boolean) = settingsDataStore.setReplayGain(enabled)
    suspend fun setAccentColor(hex: String) = settingsDataStore.setAccentColor(hex)
    suspend fun setOnboardingCompleted(completed: Boolean) = settingsDataStore.setOnboardingCompleted(completed)
}
