package com.hezi.juyumao.ui.browse

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.ui.components.EditTagsDialog
import com.hezi.juyumao.ui.components.SongDetailDialog
import com.hezi.juyumao.ui.components.SongListItem
import kotlin.random.Random
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * 曲库页（v4.2 新布局）：
 * ┌──────────────────────────────────┐
 * │ 曲库（紧贴状态栏，同首页标题位置）      │
 * ├──────────────────────────────────┤
 * │ 🔍 胶囊搜索栏                       │
 * ├──────────────────────────────────┤
 * │ ♪ 全部音乐   N 首            ›    │
 * │ 📱 本地音乐   N 首            ›    │
 * │ ☁ NAS音乐    N 首            ›    │
 * ├──────────────────────────────────┤
 * │  （下方空置：歌单列表，待用户自建）     │
 * └──────────────────────────────────┘
 * 点入口进入对应歌曲列表（SongListItem 统一格式，歌词 10s 随机刷新）。
 */
@Composable
fun BrowseScreen(
    onSongClick: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val batchState by viewModel.batchCacheState.collectAsStateWithLifecycle()
    val nasConnected by viewModel.nasConnected.collectAsStateWithLifecycle()
    val albumNames by viewModel.albumNames.collectAsStateWithLifecycle()
    val artistNames by viewModel.artistNames.collectAsStateWithLifecycle()
    val genreNames by viewModel.genreNames.collectAsStateWithLifecycle()
    val dimensionSongs by viewModel.dimensionSongs.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val topPlayed by viewModel.topPlayed.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloadMessage by viewModel.downloadMessage.collectAsStateWithLifecycle()
    val editMessage by viewModel.editMessage.collectAsStateWithLifecycle()

    // 列表筛选：null=入口页，"all"/"local"/"smb"=歌曲列表，"dims"=维度选择，
    // "album"/"artist"/"genre"=维度名列表，"dim_songs"=维度歌曲
    var listFilter by remember { mutableStateOf<String?>(null) }
    // 维度浏览状态：当前维度类型 + 选中的维度名
    var dimType by remember { mutableStateOf("album") }
    var dimName by remember { mutableStateOf("") }

    // 多选模式（P0-4）
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchRating by remember { mutableStateOf(false) }
    var showBatchPlaylist by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // 歌词每 10 秒随机刷新一句（全局 tick，各歌独立随机）
    var lyricTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            lyricTick++
        }
    }

    // 列表行操作弹窗状态
    var addToPlaylistSong by remember { mutableStateOf<SongEntity?>(null) }
    var detailSong by remember { mutableStateOf<SongEntity?>(null) }
    var editTagsSong by remember { mutableStateOf<SongEntity?>(null) }

    val localCount = allSongs.count { it.source == "LOCAL" }
    // NAS 未连接时列表清空展示（数据库缓存保留，重连后自动恢复，无需重新缓存）
    val smbCount = if (nasConnected) allSongs.count { it.source == "SMB" } else 0

    if (listFilter == null) {
        // ═══ 入口页 ═══
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题（同首页"局域猫播放器"位置：statusBarsPadding + 4dp 顶部）
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("曲库", style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground)
                IconButton(onClick = onNavigateToSearch) {
                    Icon(MiuixIcons.Search, "搜索")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 胶囊搜索栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                cornerRadius = 24.dp,
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = onNavigateToSearch,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(MiuixIcons.Search, null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(18.dp))
                    Text("搜索歌曲", style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 三个音乐入口
            LibraryEntryRow(
                icon = { Icon(MiuixIcons.Music, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                title = "全部音乐",
                subtitle = "${allSongs.size} 首",
                onClick = { listFilter = "all" },
            )
            LibraryEntryRow(
                icon = { Icon(Icons.Default.PhoneAndroid, null, // miuix-icons 无对应，保留 material icon
                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                title = "本地音乐",
                subtitle = "$localCount 首",
                onClick = { listFilter = "local" },
            )
            LibraryEntryRow(
                icon = { Icon(MiuixIcons.CloudFill, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                title = "NAS音乐",
                subtitle = "$smbCount 首",
                onClick = { listFilter = "smb" },
            )
            LibraryEntryRow(
                icon = { Icon(MiuixIcons.GridView, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                title = "按专辑 / 艺术家浏览",
                subtitle = "${albumNames.size} 张专辑 · ${artistNames.size} 位艺术家",
                onClick = { listFilter = "dims" },
            )
            LibraryEntryRow(
                icon = { Icon(MiuixIcons.TopDownloads, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                title = "最近添加",
                subtitle = "${recentlyAdded.size} 首",
                onClick = { listFilter = "recent_added" },
            )
            LibraryEntryRow(
                icon = { Icon(MiuixIcons.FavoritesFill, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                title = "播放最多",
                subtitle = "${topPlayed.size} 首",
                onClick = { listFilter = "top_played" },
            )

            // 下方空置：歌单列表（待用户自建歌单后填充）
            Spacer(modifier = Modifier.weight(1f))
            Text("歌单功能即将上线", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 120.dp))
        }
    } else if (listFilter == "dims") {
        // ═══ 维度选择页（专辑 / 艺术家 / 流派 / 文件夹）═══
        DimensionPickerPage(
            albumCount = albumNames.size,
            artistCount = artistNames.size,
            genreCount = genreNames.size,
            folderCount = folders.size,
            onBack = { listFilter = null },
            onPickAlbum = { listFilter = "album" },
            onPickArtist = { listFilter = "artist" },
            onPickGenre = { listFilter = "genre" },
            onPickFolder = { listFilter = "folder" },
        )
    } else if (listFilter == "album" || listFilter == "artist" || listFilter == "genre" || listFilter == "folder") {
        // ═══ 维度名列表页 ═══
        val names = when (listFilter) {
            "album" -> albumNames
            "artist" -> artistNames
            "folder" -> folders
            else -> genreNames
        }
        val dimTitle = when (listFilter) {
            "album" -> "专辑"
            "artist" -> "艺术家"
            "folder" -> "文件夹"
            else -> "流派"
        }
        DimensionNamesPage(
            title = dimTitle,
            names = names,
            onBack = { listFilter = "dims" },
            onPick = { name ->
                dimType = listFilter!!
                dimName = name
                if (listFilter == "folder") {
                    viewModel.loadFolderSongs(name)
                } else {
                    viewModel.loadDimensionSongs(listFilter!!, name)
                }
                listFilter = "dim_songs"
            },
        )
    } else {
        // ═══ 歌曲列表页 ═══
        val songs = when (listFilter) {
            "local" -> allSongs.filter { it.source == "LOCAL" }
            "smb" -> if (nasConnected) allSongs.filter { it.source == "SMB" } else emptyList()
            "dim_songs" -> dimensionSongs
            "recent_added" -> recentlyAdded
            "top_played" -> topPlayed
            else -> allSongs
        }
        val title = when (listFilter) {
            "local" -> "本地音乐"
            "smb" -> "NAS音乐"
            "dim_songs" -> dimName.ifBlank { "歌曲" }
            "recent_added" -> "最近添加"
            "top_played" -> "播放最多"
            else -> "全部音乐"
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // 列表页顶栏（返回 + 标题 + 多选，同样紧贴状态栏）
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (selectionMode) {
                        selectionMode = false
                        selectedIds = emptySet()
                    } else {
                        listFilter = null
                    }
                }) {
                    Icon(if (selectionMode) MiuixIcons.Close2 else MiuixIcons.Back,
                        if (selectionMode) "退出多选" else "返回")
                }
                Text(if (selectionMode) "已选 ${selectedIds.size} 首" else title,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground)
                if (selectionMode) {
                    IconButton(onClick = {
                        selectedIds = if (selectedIds.size == songs.size)
                            emptySet()
                        else songs.map { it.id }.toSet()
                    }) {
                        Icon(MiuixIcons.SelectAll, "全选/取消全选")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (listFilter == "dim_songs") {
                            TextButton(text = "播放全部",
                                onClick = { viewModel.playAll(songs) },
                                textStyle = MiuixTheme.textStyles.footnote1,
                                colors = ButtonDefaults.textButtonColors(
                                    textColor = MiuixTheme.colorScheme.primary))
                        }
                        IconButton(onClick = {
                            selectionMode = true
                            selectedIds = emptySet()
                        }) {
                            Icon(MiuixIcons.SelectAll, "多选")
                        }
                    }
                }
            }

            // 专辑详情头部（P0-5）：封面大图 + 专辑名 + 艺术家 + 曲数
            if (listFilter == "dim_songs" && dimType == "album" && songs.isNotEmpty()) {
                val albumCover = songs.firstOrNull()?.albumArtUri
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                    cornerRadius = 14.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (!albumCover.isNullOrEmpty()) {
                            AsyncImage(model = java.io.File(albumCover), contentDescription = null,
                                modifier = Modifier.size(72.dp).squircleClip(cornerRadius = 12.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Box(modifier = Modifier.size(72.dp)
                                .squircleBackground(color = MiuixTheme.colorScheme.primary.copy(0.12f),
                                    cornerRadius = 12.dp),
                                contentAlignment = Alignment.Center) {
                                Icon(MiuixIcons.Music, null,
                                    tint = MiuixTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(32.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dimName, style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onSurface, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(songs.firstOrNull()?.artist ?: "", style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text("${songs.size} 首歌曲", style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // 批量缓存进度条（NAS 重连后元数据缓存中显示）
            if (batchState.isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
                    cornerRadius = 10.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("正在缓存歌曲元数据... (${batchState.processed}/${batchState.total})",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onPrimaryContainer)
                        LinearProgressIndicator(progress = batchState.progress, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            listFilter == "smb" && !nasConnected -> "NAS 未连接，请先到设置中连接"
                            allSongs.isEmpty() -> "暂无歌曲，请先扫描本地音乐"
                            else -> "暂无歌曲"
                        },
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp),
                ) {
                    items(songs, key = { it.id }) { song ->
                        // 封面按需补提取（NAS 歌未预热时）
                        LaunchedEffect(song.id, song.albumArtUri) {
                            if (song.albumArtUri.isNullOrEmpty()) viewModel.ensureArtwork(song)
                        }
                        // 歌词行：加载一次（key=song.id），每 10s tick 随机换一句
                        val lyricLines by produceState<List<String>>(emptyList(), song.id) {
                            value = viewModel.loadLyricLines(song)
                        }
                        val lyricLine = remember(lyricLines, lyricTick) {
                            if (lyricLines.isEmpty()) null
                            else lyricLines[Random.nextInt(lyricLines.size)]
                        }
                        SongListItem(
                            song = song,
                            lyricLine = lyricLine,
                            onClick = { onSongClick(song.id) },
                            onPlay = { onSongClick(song.id) },
                            onPlayNext = { viewModel.playNext(song) },
                            onPlayLater = { viewModel.playLater(song) },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            onAddToPlaylist = { addToPlaylistSong = song },
                            onShowDetail = { detailSong = song },
                            onDownload = if (listFilter == "smb") {
                                { viewModel.downloadNasSong(song) }
                            } else null,
                            selectionMode = selectionMode,
                            selected = song.id in selectedIds,
                            onToggleSelect = {
                                selectedIds = if (song.id in selectedIds)
                                    selectedIds - song.id
                                else selectedIds + song.id
                            },
                        )
                    }
                }
            }

            // 多选模式底部操作栏（P0-4）
            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BatchAction(icon = MiuixIcons.FavoritesFill, label = "收藏", onClick = {
                        if (selectedIds.isNotEmpty()) {
                            viewModel.batchSetFavorite(selectedIds, true)
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    })
                    BatchAction(icon = Icons.Filled.Star, label = "评分", onClick = { showBatchRating = true })
                    BatchAction(icon = MiuixIcons.Playlist, label = "加歌单", onClick = { showBatchPlaylist = true })
                    BatchAction(icon = MiuixIcons.Delete, label = "移除", onClick = { showBatchDeleteConfirm = true }, danger = true)
                }
            }
        }
    }

    // 列表行：添加到歌单弹窗
    addToPlaylistSong?.let { song ->
        com.hezi.juyumao.ui.playlist.AddToPlaylistDialog(
            song = song,
            onDismiss = { addToPlaylistSong = null },
        )
    }
    // 列表行：歌曲详情弹窗
    detailSong?.let { song ->
        SongDetailDialog(
            song = song,
            onDismiss = { detailSong = null },
            onPlay = { detailSong = null; onSongClick(song.id) },
            onToggleFavorite = { detailSong = null; viewModel.toggleFavorite(song) },
            onAddToPlaylist = { detailSong = null; addToPlaylistSong = song },
            onPlayNext = { detailSong = null; viewModel.playNext(song) },
            onSetRating = { rating -> detailSong = null; viewModel.setRating(song, rating) },
            onEditTags = { detailSong = null; editTagsSong = song },
        )
    }

    // 编辑标签对话框（仅本地歌曲）
    editTagsSong?.let { song ->
        EditTagsDialog(
            song = song,
            onDismiss = { editTagsSong = null },
            onSave = { title, artist, album, genre, year ->
                editTagsSong = null
                viewModel.editTags(song, title, artist, album, genre, year)
            },
        )
    }

    // 编辑标签结果提示（一次性）
    editMessage?.let { msg ->
        OverlayDialog(
            show = true,
            title = "提示",
            onDismissRequest = { viewModel.consumeEditMessage() },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Text(msg, style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "确定", onClick = { viewModel.consumeEditMessage() })
                }
            }
        }
    }

    // NAS 下载结果提示（一次性）
    downloadMessage?.let { msg ->
        OverlayDialog(
            show = true,
            title = "提示",
            onDismissRequest = { viewModel.consumeDownloadMessage() },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Text(msg, style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "确定", onClick = { viewModel.consumeDownloadMessage() })
                }
            }
        }
    }

    // 批量评分对话框
    if (showBatchRating) {
        OverlayDialog(
            show = true,
            title = "批量评分（${selectedIds.size} 首）",
            onDismissRequest = { showBatchRating = false },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..5).forEach { star ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = SinkFeedback(
                                    sinkAmount = 0.85f,
                                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                ),
                            ) {
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.batchSetRating(selectedIds, star)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                }
                                showBatchRating = false
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Star, null, tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Text("$star 星", style = MiuixTheme.textStyles.body1)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(text = "取消", onClick = { showBatchRating = false })
                }
            }
        }
    }

    // 批量加歌单对话框
    if (showBatchPlaylist) {
        OverlayDialog(
            show = true,
            title = "加入歌单（${selectedIds.size} 首）",
            onDismissRequest = { showBatchPlaylist = false },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (playlists.isEmpty()) {
                    Text("暂无歌单，请先到歌单页新建", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                } else {
                    playlists.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = SinkFeedback(
                                        sinkAmount = 0.85f,
                                        animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                    ),
                                ) {
                                    viewModel.batchAddToPlaylist(selectedIds, p.id)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                    showBatchPlaylist = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(MiuixIcons.Playlist, null, tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Text(p.name, style = MiuixTheme.textStyles.body1)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(text = "取消", onClick = { showBatchPlaylist = false })
                }
            }
        }
    }

    // 批量移除确认对话框
    if (showBatchDeleteConfirm) {
        OverlayDialog(
            show = true,
            title = "移除歌曲（${selectedIds.size} 首）",
            onDismissRequest = { showBatchDeleteConfirm = false },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Text("将把这 ${selectedIds.size} 首从曲库移除（保留文件），确定？",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(text = "取消", onClick = { showBatchDeleteConfirm = false })
                    TextButton(text = "移除", onClick = {
                        viewModel.batchDelete(selectedIds)
                        selectionMode = false
                        selectedIds = emptySet()
                        showBatchDeleteConfirm = false
                    })
                }
            }
        }
    }
}

/** 曲库入口行：图标 + 标题 + 数量 + 右箭头 */
@Composable
private fun LibraryEntryRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
        cornerRadius = 14.dp,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 图标底
            Box(
                modifier = Modifier.size(40.dp)
                    .squircleBackground(color = MiuixTheme.colorScheme.primary.copy(0.1f), cornerRadius = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface, maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
            }
            Icon(MiuixIcons.ChevronForward, null,
                tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** 维度选择页：专辑 / 艺术家 / 流派 / 文件夹入口 */
@Composable
private fun DimensionPickerPage(
    albumCount: Int,
    artistCount: Int,
    genreCount: Int,
    folderCount: Int,
    onBack: () -> Unit,
    onPickAlbum: () -> Unit,
    onPickArtist: () -> Unit,
    onPickGenre: () -> Unit,
    onPickFolder: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏（同列表页：紧贴状态栏）
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
            Text("维度浏览", style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LibraryEntryRow(
            icon = { Icon(MiuixIcons.Album, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
            title = "专辑",
            subtitle = "$albumCount 张",
            onClick = onPickAlbum,
        )
        LibraryEntryRow(
            icon = { Icon(MiuixIcons.Mic, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
            title = "艺术家",
            subtitle = "$artistCount 位",
            onClick = onPickArtist,
        )
        LibraryEntryRow(
            icon = { Icon(MiuixIcons.Tune, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
            title = "流派",
            subtitle = "$genreCount 种",
            onClick = onPickGenre,
        )
        LibraryEntryRow(
            icon = { Icon(MiuixIcons.Folder, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
            title = "文件夹",
            subtitle = "$folderCount 个",
            onClick = onPickFolder,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** 维度名列表页：专辑/艺术家/流派名称，点击进入该维度歌曲列表 */
@Composable
private fun DimensionNamesPage(
    title: String,
    names: List<String>,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏（同列表页）
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
            Text(title, style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.size(48.dp))
        }
        if (names.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无$title", style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(names, key = { it }) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = SinkFeedback(
                                    sinkAmount = 0.85f,
                                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                ),
                                onClick = { onPick(name) },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(name, style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Icon(MiuixIcons.ChevronForward, null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** 多选模式底部操作按钮：图标 + 小字 */
@Composable
private fun BatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, label,
            tint = if (danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp))
        Text(label, style = MiuixTheme.textStyles.footnote2,
            color = if (danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface)
    }
}
