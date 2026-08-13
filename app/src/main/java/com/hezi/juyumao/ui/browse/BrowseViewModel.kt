package com.hezi.juyumao.ui.browse

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.lyrics.LyricsManager
import com.hezi.juyumao.data.local.metadata.BatchCacheState
import com.hezi.juyumao.data.local.metadata.MetadataBatchProcessor
import com.hezi.juyumao.data.repository.MetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val songDao: SongDao,
    private val metadataRepository: MetadataRepository,
    private val metadataBatchProcessor: MetadataBatchProcessor,
    private val lyricsManager: LyricsManager,
) : ViewModel() {

    val allSongs: StateFlow<List<SongEntity>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 收藏歌曲（我喜欢 Tab） */
    val favorites: StateFlow<List<SongEntity>> = songDao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 专辑/艺术家/流派浏览（T10.8） */
    val albumNames: StateFlow<List<String>> = songDao.getAlbumNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artistNames: StateFlow<List<String>> = songDao.getArtistNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genreNames: StateFlow<List<String>> = songDao.getGenreNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中维度（专辑/艺术家/流派名）对应的歌曲 */
    private val _dimensionSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val dimensionSongs: StateFlow<List<SongEntity>> = _dimensionSongs

    fun loadDimensionSongs(type: String, name: String) {
        viewModelScope.launch {
            _dimensionSongs.value = when (type) {
                "album" -> songDao.getSongsByAlbum(name).first()
                "artist" -> songDao.getSongsByArtist(name).first()
                "genre" -> songDao.getSongsByGenre(name).first()
                else -> emptyList()
            }
        }
    }

    /** 批量缓存进度 */
    val batchCacheState: StateFlow<BatchCacheState> = metadataBatchProcessor.state

    /** 歌词加载并发限制（曲库列表多行同时加载歌词，SMB 需下载，避免 IO 风暴） */
    private val lyricLoadSemaphore = Semaphore(3)

    /** 加载歌曲歌词并返回"避开首尾"的歌词行（供列表 10 秒随机刷新一句） */
    suspend fun loadLyricLines(song: SongEntity): List<String> = lyricLoadSemaphore.withPermit {
        val data = lyricsManager.getLyrics(song) ?: return@withPermit emptyList()
        val lines = data.lines.map { it.text }.filter { it.isNotBlank() }
        if (lines.size <= 2) return@withPermit emptyList()
        lines.drop(1).dropLast(1)
    }    /** 正在提取封面的 songId 集合（避免并发重复），失败后允许重试 */
    private val artworkInFlight = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * 按需提取 NAS 歌曲封面（列表项显示时调用一次）
     * 成功后更新数据库，下次直接从缓存加载
     */
    fun ensureArtwork(song: SongEntity) {
        // 已有封面或正在提取，跳过
        if (!song.albumArtUri.isNullOrEmpty()) return
        if (song.source != "SMB") return
        if (song.id in artworkInFlight.value) return

        artworkInFlight.value = artworkInFlight.value + song.id

        viewModelScope.launch {
            try {
                val path = metadataRepository.extractAndCacheArtwork(song)
                if (path != null) {
                    songDao.update(song.copy(albumArtUri = path))
                    Log.d("BrowseVM", "封面已提取: ${song.title}")
                } else {
                    Log.w("BrowseVM", "封面提取无结果: ${song.title}")
                }
            } catch (e: Exception) {
                Log.e("BrowseVM", "封面提取失败: ${song.title}", e)
            } finally {
                artworkInFlight.value = artworkInFlight.value - song.id
            }
        }
    }

    /** 切换歌曲收藏状态（列表收藏入口共用） */
    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            songDao.updateFavorite(song.id, !song.isFavorite)
        }
    }
}
