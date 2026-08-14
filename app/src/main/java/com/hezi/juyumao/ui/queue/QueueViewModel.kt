package com.hezi.juyumao.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.dao.PlaylistDao
import com.hezi.juyumao.data.local.db.entity.PlaylistEntity
import com.hezi.juyumao.data.local.db.entity.PlaylistSongEntity
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
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 保存队列结果提示（成功/失败），UI 一次性消费 */
    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun consumeSaveMessage() {
        _saveMessage.value = null
    }

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

    /** 队列内移动（上移/下移） */
    fun move(from: Int, to: Int) {
        playbackController.moveQueue(from, to)
    }

    /** 保存当前队列为新歌单 */
    fun saveAsPlaylist(name: String) {
        viewModelScope.launch {
            val songs = _queue.value
            if (songs.isEmpty()) {
                _saveMessage.value = "队列为空，无法保存"
                return@launch
            }
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                _saveMessage.value = "歌单名不能为空"
                return@launch
            }
            try {
                val playlistId = playlistDao.insert(PlaylistEntity(name = trimmed))
                playlistDao.addSongs(songs.map { PlaylistSongEntity(playlistId, it.id) })
                _saveMessage.value = "已保存到歌单「$trimmed」（${songs.size} 首）"
            } catch (e: Exception) {
                _saveMessage.value = "保存失败: ${e.message}"
            }
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            playbackController.clearQueue()
        }
    }
}
