package com.hezi.juyumao.ui.smartplaylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.dao.SmartPlaylistDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SmartPlaylistEntity
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(
    private val smartPlaylistDao: SmartPlaylistDao,
    private val songDao: SongDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val playlists: StateFlow<List<SmartPlaylistEntity>> = smartPlaylistDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _current = MutableStateFlow<SmartPlaylistEntity?>(null)
    val current: StateFlow<SmartPlaylistEntity?> = _current.asStateFlow()

    private val _songs = MutableStateFlow<List<SongEntity>>(emptyList())
    val songs: StateFlow<List<SongEntity>> = _songs.asStateFlow()

    /** 打开智能歌单并动态查询歌曲 */
    fun open(playlist: SmartPlaylistEntity) {
        _current.value = playlist
        viewModelScope.launch {
            _songs.value = query(playlist)
        }
    }

    fun close() {
        _current.value = null
        _songs.value = emptyList()
    }

    /** 新建或更新智能歌单 */
    fun save(playlist: SmartPlaylistEntity, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) smartPlaylistDao.insert(playlist)
            else smartPlaylistDao.update(playlist)
        }
    }

    /** 删除智能歌单 */
    fun delete(playlist: SmartPlaylistEntity) {
        viewModelScope.launch {
            smartPlaylistDao.delete(playlist)
            if (_current.value?.id == playlist.id) close()
        }
    }

    /** 播放全部 */
    fun playAll() {
        if (_songs.value.isNotEmpty()) {
            playbackController.loadPlaylist(_songs.value, 0)
        }
    }

    /** 下一首播放 */
    fun playNext(song: SongEntity) {
        playbackController.playNext(song)
    }

    /** 稍后播放 */
    fun playLater(song: SongEntity) {
        playbackController.addToQueue(song)
    }

    /** 切换收藏 */
    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            songDao.updateFavorite(song.id, !song.isFavorite)
        }
    }

    private suspend fun query(p: SmartPlaylistEntity): List<SongEntity> {
        val cutoff = System.currentTimeMillis() - p.addedWithinDays * 86_400_000L
        return songDao.querySmartPlaylist(
            minRating = p.minRating,
            minPlayCount = p.minPlayCount,
            genre = p.genre,
            source = p.source,
            onlyFav = if (p.isFavoriteOnly) 1 else 0,
            withinDays = p.addedWithinDays,
            cutoff = cutoff,
        )
    }
}
