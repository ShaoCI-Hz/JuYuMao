package com.hezi.juyumao.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.local.lyrics.LyricsManager
import com.hezi.juyumao.data.local.metadata.TagEditor
import com.hezi.juyumao.data.remote.smb.SmbConnectionState
import com.hezi.juyumao.data.repository.MusicRepository
import com.hezi.juyumao.data.repository.SmbRepository
import com.hezi.juyumao.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.random.Random
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

data class DailyCardData(
    val greeting: String,
    val dateText: String,
    val quote: String,
    val quoteAuthor: String,
    val weatherText: String? = null,
)

data class HomeUiState(
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val playCount: Long = 0,
    val totalSize: Long = 0L,
    val isScanning: Boolean = false,
    val scanMessage: String = "",
    val recentlyPlayed: List<SongEntity> = emptyList(),
    val dailyCard: DailyCardData = generateDailyCard(),
    val nasConnected: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
    smbRepository: SmbRepository,
    private val lyricsManager: LyricsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    /** 首页动态推荐歌曲：启动随机选一首，之后每 10 秒自动切换（动态推荐感） */
    private val _featuredSong = MutableStateFlow<SongEntity?>(null)
    val featuredSong: StateFlow<SongEntity?> = _featuredSong

    /** 全量歌曲缓存（随机推荐的数据池，随数据库变化更新） */
    private var allSongs: List<SongEntity> = emptyList()

    /** 首页歌词卡：随机歌曲的随机歌词行，每 10 秒刷新一句 */
    private val _dailyLyric = MutableStateFlow<String?>(null)
    val dailyLyric: StateFlow<String?> = _dailyLyric

    private var lastWeatherFetchDay: Long = -1

    init {
        viewModelScope.launch {
            combine(
                musicRepository.getSongCount(),
                musicRepository.getTotalSize(),
                musicRepository.getAlbumCount(),
                musicRepository.getArtistCount(),
                musicRepository.getTotalPlayCount(),
            ) { count, size, albums, artists, playCount ->
                _uiState.value.copy(
                    songCount = count, totalSize = size ?: 0L,
                    albumCount = albums, artistCount = artists,
                    playCount = playCount,
                )
            }.collect { _uiState.value = it }
        }
        // 周期性刷新 NAS 连接状态（连接池是内存态，无法用 Flow 监听）
        viewModelScope.launch {
            while (true) {
                val connected = smbRepository.isAnyConnected()
                _uiState.value = _uiState.value.copy(nasConnected = connected)
                kotlinx.coroutines.delay(3000)
            }
        }
        viewModelScope.launch {
            musicRepository.getRecentlyPlayed().collect { songs ->
                _uiState.value = _uiState.value.copy(recentlyPlayed = songs)
            }
        }
        // 动态推荐：全量歌曲随机选一首作为首页推荐（打开即推荐）。
        // 仅在歌曲总数变化时重选：封面预热等逐首 update 不改数量，避免推荐卡频繁闪烁
        viewModelScope.launch {
            musicRepository.getAllSongs().collect { songs ->
                val sizeChanged = songs.size != allSongs.size
                allSongs = songs
                if (sizeChanged && songs.isNotEmpty()) _featuredSong.value = songs.random()
            }
        }
        // 动态推荐：每 10 秒自动切换一首
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                if (allSongs.isNotEmpty()) _featuredSong.value = allSongs.random()
            }
        }
        // 首页歌词卡：每 10 秒随机选一首歌、随机取一句中段歌词
        viewModelScope.launch {
            while (true) {
                if (allSongs.isNotEmpty()) {
                    val lines = loadLyricLines(allSongs.random())
                    if (lines.isNotEmpty()) {
                        _dailyLyric.value = lines[Random.nextInt(lines.size)]
                    }
                }
                kotlinx.coroutines.delay(10_000)
            }
        }
        // 异步获取天气
        refreshWeather()
    }

    /** 歌词加载并发限制：最近播放列表多首歌同时 produceState 加载歌词，
     *  SMB 歌词需下载文件，无限制并发会造成网络/IO 风暴拖慢 UI */
    private val lyricLoadSemaphore = Semaphore(3)

    /**
     * 加载歌曲歌词并返回"避开首尾"的歌词行（供最近播放列表 10 秒随机刷新一句）。
     * 无歌词或歌词行过短时返回空列表（UI 隐藏歌词行）。
     */
    suspend fun loadLyricLines(song: SongEntity): List<String> = lyricLoadSemaphore.withPermit {
        val data = lyricsManager.getLyrics(song) ?: return@withPermit emptyList()
        val lines = data.lines.map { it.text }.filter { it.isNotBlank() }
        if (lines.size <= 2) return@withPermit emptyList()
        // 避开首尾歌词（各去 1 句，保证是"中段"歌词）
        lines.drop(1).dropLast(1)
    }

    /** 下一首播放（插入队列当前+1，不打断播放） */
    fun playNext(song: SongEntity) {
        playbackController.playNext(song)
    }

    /** 稍后播放（追加到队列末尾） */
    fun playLater(song: SongEntity) {
        playbackController.addToQueue(song)
    }

    /** 切换收藏状态 */
    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(song.id, !song.isFavorite)
        }
    }

    /** 更新星级评分 */
    fun setRating(song: SongEntity, rating: Int) {
        viewModelScope.launch {
            musicRepository.updateRating(song.id, rating)
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
                    musicRepository.updateTags(song.id, title, artist, album, genre, year)
                    _editMessage.value = "标签已更新"
                },
                onFailure = { _editMessage.value = "编辑失败: ${it.message}" },
            )
        }
    }

    fun scanLocalMusic() {
        // 防重入：扫描中重复点击直接忽略（避免并行扫描、状态被旧任务覆盖）
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, scanMessage = "扫描中...")
            val result = musicRepository.scanLocalMusic()
            result.fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(isScanning = false, scanMessage = "扫描完成，找到 $count 首歌曲")
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isScanning = false, scanMessage = "扫描失败: ${e.message}")
                },
            )
        }
    }

    private fun refreshWeather() {
        // 节流：同一天只请求一次（进程生命周期内）
        val today = LocalDateTime.now().toLocalDate().toEpochDay()
        if (lastWeatherFetchDay == today) return
        lastWeatherFetchDay = today
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 用 3 行纯文本格式，避免编码问题
                val conn = java.net.URL("https://wttr.in/?format=3").openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "curl/7.64.1")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val raw = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()
                // raw 格式: "城市 +28°C 晴" 或 "+28°C 晴"
                if (raw.isNotEmpty() && !raw.contains("Unknown") && !raw.contains("ERROR") && !raw.contains("Sorry")) {
                    // 提取温度和天气描述
                    val weather = raw.replace(Regex(".*?([+-]?\\d+°C.*)"), "$1").trim()
                    if (weather.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            dailyCard = _uiState.value.dailyCard.copy(weatherText = weather)
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

private val musicQuotes = listOf(
    "音乐是灵魂的语言，它能说出语言无法表达的东西。" to "贝多芬",
    "没有音乐，生命是一个错误。" to "尼采",
    "音乐是比一切智慧、一切哲学更高的启示。" to "贝多芬",
    "当我听到音乐时，我便忘记了自己。" to "玛丽莲·梦露",
    "音乐是思维着的声音。" to "雨果",
    "最好的音乐是在你最需要的时候听到的。" to "爱默生",
    "音乐是人类的通用语言。" to "朗费罗",
    "音乐是生活中最美好的一面。" to "丘吉尔",
    "没有音乐的世界是不完整的。" to "莫扎特",
    "音乐能抚慰野蛮的胸膛，能软化坚硬的石头。" to "莎士比亚",
    "音乐是唯一可以纵情而不会损害道德和宗教观念的享受。" to "爱迪生",
    "音乐中蕴藏着如此悦耳的催人奋进的力量。" to "弥尔顿",
    "音乐是建筑在音响基础上的艺术。" to "海顿",
    "音乐使一个民族的气质更高贵。" to "福楼拜",
    "音乐是开启人类智慧宝库的钥匙。" to "雨果",
    "音乐是耳朵的眼睛。" to "塞万提斯",
    "没有热情，就不可能创造出任何真正的艺术作品。" to "舒曼",
    "音乐是心灵的进发。" to "柏辽兹",
    "音乐是上天给人类最伟大的礼物。" to "肖邦",
    "音乐表达的是无法用语言说出的东西。" to "雨果",
    "音乐是不假任何外力，直接沁人心脾的最纯的感情火焰。" to "李斯特",
    "音乐用理想的纽带把人类结合在一起。" to "瓦格纳",
    "音乐是人生的艺术。" to "施特劳斯",
    "没有早期音乐教育，干什么事我都会一事无成。" to "爱因斯坦",
    "音乐应当使人类的精神爆发出火花。" to "贝多芬",
    "此曲只应天上有，人间能得几回闻。" to "杜甫",
    "嘈嘈切切错杂弹，大珠小珠落玉盘。" to "白居易",
    "清风吹歌入空去，歌曲自绕行云飞。" to "李白",
    "音乐，是人生最大的快乐；音乐，是生活中的一股清泉。" to "冼星海",
    "真正创作音乐的是人民，作曲家只不过把它们编成曲子而已。" to "格林卡",
    "不爱音乐不配做人。" to "黑格尔",
    "要尊崇过去的遗产，但也要一片至诚地迎接新的萌芽。" to "舒曼",
    "技术只有到了高尚的手中，才会变得像歌唱一样优美。" to "李斯特",
    "假如我的音乐只能使人愉快，那我很遗憾，我的目的是使人高尚。" to "亨德尔",
    "通过音乐并在音乐中教育我们的孩子。" to "海伦·凯勒",
    "对美的感知和理解是审美教育的核心。" to "苏霍姆林斯基",
    "音乐教育并不是音乐家的教育，而首先是人的教育。" to "苏霍姆林斯基",
    "一字新声一颗珠，转喉疑是击珊瑚。" to "薛能",
)

fun generateDailyCard(): DailyCardData {
    val now = LocalDateTime.now()
    val greeting = when (now.hour) {
        in 0..5 -> "夜深了"
        in 6..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..21 -> "晚上好"
        else -> "夜深了"
    }
    val weekDay = arrayOf("星期一","星期二","星期三","星期四","星期五","星期六","星期日")[now.dayOfWeek.value - 1]
    val dateText = "${now.monthValue}月${now.dayOfMonth}日 $weekDay"
    val seed = kotlin.math.abs(now.toLocalDate().toEpochDay().toInt())
    val (quote, author) = musicQuotes[seed % musicQuotes.size]
    return DailyCardData(greeting = greeting, dateText = dateText, quote = quote, quoteAuthor = author)
}
