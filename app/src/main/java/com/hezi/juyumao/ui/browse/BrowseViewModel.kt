package com.hezi.juyumao.ui.browse

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.cache.CacheManager
import com.hezi.juyumao.data.local.crypto.decryptPassword
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.dao.PlaylistDao
import com.hezi.juyumao.data.local.db.entity.PlaylistSongEntity
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.lyrics.LyricsManager
import com.hezi.juyumao.data.local.metadata.BatchCacheState
import com.hezi.juyumao.data.local.metadata.MetadataBatchProcessor
import com.hezi.juyumao.data.local.metadata.TagEditor
import com.hezi.juyumao.data.remote.smb.SmbConnectionPool
import com.hezi.juyumao.data.repository.MetadataRepository
import com.hezi.juyumao.data.repository.SmbRepository
import com.hezi.juyumao.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val songDao: SongDao,
    private val metadataRepository: MetadataRepository,
    private val metadataBatchProcessor: MetadataBatchProcessor,
    private val lyricsManager: LyricsManager,
    private val playbackController: PlaybackController,
    private val cacheManager: CacheManager,
    private val serverDao: ServerDao,
    private val smbConnectionPool: SmbConnectionPool,
    private val playlistDao: PlaylistDao,
    smbRepository: SmbRepository,
) : ViewModel() {

    val allSongs: StateFlow<List<SongEntity>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** NAS 连接状态（连接池是内存态，无 Flow 监听，3 秒轮询；未连接时 NAS 列表清空展示） */
    private val _nasConnected = MutableStateFlow(false)
    val nasConnected: StateFlow<Boolean> = _nasConnected

    init {
        viewModelScope.launch {
            while (true) {
                _nasConnected.value = smbRepository.isAnyConnected()
                delay(3000)
            }
        }
    }

    /** 收藏歌曲（我喜欢 Tab） */
    val favorites: StateFlow<List<SongEntity>> = songDao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 最近添加（按入库时间倒序，最多 50） */
    val recentlyAdded: StateFlow<List<SongEntity>> = songDao.getRecentlyAdded(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 播放最多（按播放次数倒序，最多 50） */
    val topPlayed: StateFlow<List<SongEntity>> = songDao.getTopPlayedSongs(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部歌单（批量加歌单选择用） */
    val playlists: StateFlow<List<com.hezi.juyumao.data.local.db.entity.PlaylistEntity>> =
        playlistDao.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 文件夹列表（P2-15：按父目录名分组，去重排序） */
    val folders: StateFlow<List<String>> = allSongs.map { songs ->
        songs.map { it.filePath.substringBeforeLast('/', "").substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 加载某文件夹内的歌曲（复用 dimensionSongs 展示） */
    fun loadFolderSongs(folderName: String) {
        viewModelScope.launch {
            val songs = allSongs.first()
            _dimensionSongs.value = songs.filter {
                it.filePath.substringBeforeLast('/', "").substringAfterLast('/') == folderName
            }
        }
    }

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

    /** 更新星级评分 */
    fun setRating(song: SongEntity, rating: Int) {
        viewModelScope.launch {
            songDao.updateRating(song.id, rating)
        }
    }

    // ── 批量多选操作（P0-4）──

    /** 批量设置收藏 */
    fun batchSetFavorite(ids: Set<Long>, fav: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            songDao.updateFavoriteBatch(ids.toList(), fav)
        }
    }

    /** 批量设置评分 */
    fun batchSetRating(ids: Set<Long>, rating: Int) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            songDao.updateRatingBatch(ids.toList(), rating)
        }
    }

    /** 批量加入歌单 */
    fun batchAddToPlaylist(ids: Set<Long>, playlistId: Long) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            playlistDao.addSongs(ids.map { PlaylistSongEntity(playlistId, it) })
        }
    }

    /** 批量从库移除（保留文件） */
    fun batchDelete(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            songDao.deleteByIds(ids.toList())
        }
    }

    /** 编辑标签结果提示（一次性） */
    private val _editMessage = MutableStateFlow<String?>(null)
    val editMessage: StateFlow<String?> = _editMessage

    fun consumeEditMessage() {
        _editMessage.value = null
    }

    /** 编辑本地歌曲标签（写文件 + 更新数据库） */
    fun editTags(song: SongEntity, title: String, artist: String, album: String, genre: String, year: Int) {
        viewModelScope.launch {
            if (song.source != "LOCAL") {
                _editMessage.value = "仅本地歌曲可编辑标签"
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                TagEditor.editLocalTags(File(song.filePath), title, artist, album, genre, year)
            }
            result.fold(
                onSuccess = {
                    songDao.updateTags(song.id, title, artist, album, genre, year)
                    _editMessage.value = "标签已更新"
                },
                onFailure = { _editMessage.value = "编辑失败: ${it.message}" },
            )
        }
    }

    /** 下一首播放（插入队列当前+1，不打断播放） */
    fun playNext(song: SongEntity) {
        playbackController.playNext(song)
    }

    /** 稍后播放（追加到队列末尾） */
    fun playLater(song: SongEntity) {
        playbackController.addToQueue(song)
    }

    /** 播放整组歌曲（专辑/艺术家/文件夹详情共用） */
    fun playAll(songs: List<SongEntity>) {
        if (songs.isNotEmpty()) {
            playbackController.loadPlaylist(songs, 0)
        }
    }

    /** NAS 下载结果提示（一次性消费） */
    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage

    fun consumeDownloadMessage() {
        _downloadMessage.value = null
    }

    /** 下载 NAS 歌曲到本地缓存（离线播放） */
    fun downloadNasSong(song: SongEntity) {
        viewModelScope.launch {
            try {
                if (song.source != "SMB" || song.smbServerId == null || song.smbSharePath == null) {
                    _downloadMessage.value = "仅 NAS 歌曲可下载"
                    return@launch
                }
                val server = serverDao.getServerById(song.smbServerId)?.decryptPassword()
                    ?: run {
                        _downloadMessage.value = "服务器不存在"
                        return@launch
                    }
                val client = smbConnectionPool.getConnection(
                    serverId = server.id,
                    host = server.ip,
                    port = server.port,
                    username = server.username,
                    password = server.password,
                    shareName = server.effectiveShareName,
                )
                val result = withContext(Dispatchers.IO) {
                    client.openFile(song.smbSharePath!!).getOrThrow().use { input ->
                        cacheManager.saveNasSong(song.id, song.title.ifEmpty { "song" }, input)
                    }
                }
                _downloadMessage.value = "已下载: ${result.name}"
            } catch (e: Exception) {
                Log.e("BrowseVM", "NAS 下载失败", e)
                _downloadMessage.value = "下载失败: ${e.message}"
            }
        }
    }
}
