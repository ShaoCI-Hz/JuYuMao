package com.hezi.juyumao.player

import com.hezi.juyumao.domain.model.RepeatMode
import com.hezi.juyumao.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放队列管理（不通过 Hilt 注入，由 PlaybackController 直接创建）
 */
class PlaybackQueue {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleOrder = MutableStateFlow<List<Int>>(emptyList())

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        _songs.value = songs
        _currentIndex.value = if (songs.isEmpty()) -1 else startIndex.coerceIn(0, songs.lastIndex)
        _shuffleOrder.value = if (songs.isEmpty()) emptyList() else songs.indices.shuffled()
    }

    /** 外部同步当前索引（无缝模式 ExoPlayer 自然切歌时由 PlaybackController 调用，保持统计/currentSong 一致） */
    fun syncIndex(index: Int) {
        if (index in _songs.value.indices) _currentIndex.value = index
    }

    fun currentSong(): Song? {
        val idx = _currentIndex.value
        return if (idx in _songs.value.indices) _songs.value[idx] else null
    }

    fun next(repeatMode: RepeatMode, shuffle: Boolean): Song? {
        val songs = _songs.value
        if (songs.isEmpty()) return null
        val currentIdx = _currentIndex.value
        val shuffleOrder = _shuffleOrder.value

        val nextIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIdx
            RepeatMode.ALL -> {
                if (shuffle) {
                    val shuffleIdx = shuffleOrder.indexOf(currentIdx)
                    // 索引失效（并发修改）时重建随机序列
                    if (shuffleIdx == -1) {
                        _shuffleOrder.value = songs.indices.shuffled()
                        val newOrder = _shuffleOrder.value
                        newOrder[(newOrder.indexOf(currentIdx) + 1) % songs.size]
                    } else {
                        shuffleOrder[(shuffleIdx + 1) % songs.size]
                    }
                } else {
                    (currentIdx + 1) % songs.size
                }
            }
            RepeatMode.OFF -> {
                if (shuffle) {
                    var shuffleIdx = shuffleOrder.indexOf(currentIdx)
                    if (shuffleIdx == -1) {
                        _shuffleOrder.value = songs.indices.shuffled()
                        shuffleIdx = _shuffleOrder.value.indexOf(currentIdx)
                        if (shuffleIdx == -1) shuffleIdx = 0
                    }
                    if (shuffleIdx + 1 >= songs.size) return null
                    _shuffleOrder.value[shuffleIdx + 1]
                } else {
                    val next = currentIdx + 1
                    if (next >= songs.size) return null
                    next
                }
            }
        }

        _currentIndex.value = nextIndex
        return currentSong()
    }

    fun previous(repeatMode: RepeatMode, shuffle: Boolean = false): Song? {
        val songs = _songs.value
        if (songs.isEmpty()) return null
        val currentIdx = _currentIndex.value

        _currentIndex.value = when (repeatMode) {
            RepeatMode.ONE -> currentIdx // 与 next(ONE) 对称：单曲循环原地
            RepeatMode.ALL -> {
                if (shuffle) {
                    val shuffleIdx = _shuffleOrder.value.indexOf(currentIdx)
                    if (shuffleIdx == -1) {
                        _shuffleOrder.value = songs.indices.shuffled()
                        songs.size - 1
                    } else {
                        val prevIdx = if (shuffleIdx <= 0) songs.size - 1 else shuffleIdx - 1
                        _shuffleOrder.value[prevIdx]
                    }
                } else {
                    (currentIdx - 1 + songs.size) % songs.size
                }
            }
            RepeatMode.OFF -> maxOf(0, currentIdx - 1)
        }
        return currentSong()
    }

    fun playAt(index: Int) {
        if (index in _songs.value.indices) {
            _currentIndex.value = index
        }
    }

    /** 队列内移动（拖拽/上下移排序）；移动后随机顺序重置（手动排序优先于随机） */
    fun move(from: Int, to: Int) {
        val songs = _songs.value.toMutableList()
        if (from !in songs.indices || to !in songs.indices || from == to) return
        val song = songs.removeAt(from)
        songs.add(to, song)
        _songs.value = songs
        // 当前索引跟随：若移动的是当前歌，索引更新为新位置
        if (_currentIndex.value == from) {
            _currentIndex.value = to
        } else if (from < _currentIndex.value && to >= _currentIndex.value) {
            _currentIndex.value--
        } else if (from > _currentIndex.value && to <= _currentIndex.value) {
            _currentIndex.value++
        }
        _shuffleOrder.value = if (songs.isEmpty()) emptyList() else songs.indices.shuffled()
    }

    /** 下一首播放：将歌曲插入当前播放位置之后（当前歌不打断，切歌时生效） */
    fun insertNext(song: Song) {
        val songs = _songs.value.toMutableList()
        val insertIndex = if (_currentIndex.value in songs.indices) _currentIndex.value + 1 else songs.size
        songs.add(insertIndex, song)
        _songs.value = songs
        // 随机序列同步：插入点及之后的索引 +1；新歌索引插入到当前歌在随机序列中的下一个位置
        val order = _shuffleOrder.value.toMutableList()
        for (i in order.indices) {
            if (order[i] >= insertIndex) order[i]++
        }
        val currentOrderPos = order.indexOf(_currentIndex.value)
        order.add(
            if (currentOrderPos >= 0) currentOrderPos + 1 else order.size,
            insertIndex,
        )
        _shuffleOrder.value = order
    }

    /** 稍后播放：追加到队列末尾 */
    fun appendToQueue(song: Song) {
        val songs = _songs.value.toMutableList()
        songs.add(song)
        _songs.value = songs
        _shuffleOrder.value = _shuffleOrder.value + (songs.size - 1)
    }

    fun remove(index: Int) {
        val songs = _songs.value.toMutableList()
        if (index !in songs.indices) return
        val removingCurrent = index == _currentIndex.value
        val wasBeforeCurrent = index < _currentIndex.value
        songs.removeAt(index)
        _songs.value = songs
        // 保持原随机播放进度：order 中大于 index 的减一、被删索引丢弃，不再整体重建随机序列
        _shuffleOrder.value = _shuffleOrder.value
            .filter { it != index }
            .map { if (it > index) it - 1 else it }
        _currentIndex.value = when {
            songs.isEmpty() -> -1
            removingCurrent -> maxOf(0, index.coerceAtMost(songs.size - 1))
            wasBeforeCurrent -> (_currentIndex.value - 1).coerceIn(0, songs.size - 1)
            else -> _currentIndex.value.coerceIn(0, songs.size - 1)
        }
    }

    fun clear() {
        _songs.value = emptyList()
        _currentIndex.value = -1
        _shuffleOrder.value = emptyList()
    }
}
