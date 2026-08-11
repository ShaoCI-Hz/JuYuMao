package com.hezi.juyumao.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.domain.model.Song
import com.hezi.juyumao.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    init {
        // 响应式订阅：播放中队列变化（切歌/换歌单/清空）自动反映，不再需要快照式手动刷新
        viewModelScope.launch {
            playbackController.queueSongs().collect { _queue.value = it }
        }
        viewModelScope.launch {
            playbackController.queueIndex().collect { _currentIndex.value = it }
        }
    }

    fun playAt(index: Int) {
        viewModelScope.launch {
            // 队列流会自动更新当前索引，无需手动刷新
            playbackController.playAt(index)
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            playbackController.clearQueue()
        }
    }
}
