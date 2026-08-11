package com.hezi.juyumao.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 听歌报告 UI 状态 */
data class StatisticsUiState(
    val totalPlayCount: Long = 0,
    val weekPlayCount: Long = 0,
    val monthPlayCount: Long = 0,
    val totalPlayDurationMs: Long = 0,
    val topSongs: List<SongEntity> = emptyList(),
    val topArtists: List<Pair<String, Long>> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = false)
            try {
                val now = System.currentTimeMillis()
                val weekAgo = now - 7L * 24 * 3600 * 1000
                val monthAgo = now - 30L * 24 * 3600 * 1000

                val totalCount = musicRepository.getTotalPlayCount().first()
                val weekSongs = musicRepository.getSongsPlayedSince(weekAgo).first()
                val monthSongs = musicRepository.getSongsPlayedSince(monthAgo).first()

                // SQL 全量聚合：不再依赖 top200 截断列表（播放过 >200 首时总时长/艺术家榜不失真）
                val topSongs = musicRepository.getTopPlayedSongsOnce()
                val topArtists = musicRepository.getTopArtistsOnce().map { it.name to it.playCount }
                val totalPlayDurationMs = musicRepository.getTotalPlayDurationOnce()

                _uiState.value = StatisticsUiState(
                    totalPlayCount = totalCount,
                    weekPlayCount = weekSongs.count { it.playCount > 0 }.toLong(),
                    monthPlayCount = monthSongs.count { it.playCount > 0 }.toLong(),
                    totalPlayDurationMs = totalPlayDurationMs,
                    topSongs = topSongs,
                    topArtists = topArtists,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = true)
            }
        }
    }
}
