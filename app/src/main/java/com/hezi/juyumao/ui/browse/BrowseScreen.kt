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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.ui.components.SongDetailDialog
import com.hezi.juyumao.ui.components.SongListItem
import kotlin.random.Random
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    // 列表筛选：null=入口页，"all"/"local"/"smb"=对应歌曲列表
    var listFilter by remember { mutableStateOf<String?>(null) }

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

    val localCount = allSongs.count { it.source == "LOCAL" }
    val smbCount = allSongs.count { it.source == "SMB" }

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
                    .padding(horizontal = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = SinkFeedback(
                            sinkAmount = 0.85f,
                            animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                        ),
                        onClick = onNavigateToSearch,
                    ),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                cornerRadius = 24.dp,
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

            // 下方空置：歌单列表（待用户自建歌单后填充）
            Spacer(modifier = Modifier.weight(1f))
            Text("歌单功能即将上线", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 120.dp))
        }
    } else {
        // ═══ 歌曲列表页 ═══
        val songs = when (listFilter) {
            "local" -> allSongs.filter { it.source == "LOCAL" }
            "smb" -> allSongs.filter { it.source == "SMB" }
            else -> allSongs
        }
        val title = when (listFilter) {
            "local" -> "本地音乐"
            "smb" -> "NAS音乐"
            else -> "全部音乐"
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // 列表页顶栏（返回 + 标题，同样紧贴状态栏）
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { listFilter = null }) { Icon(MiuixIcons.Back, "返回") }
                Text(title, style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.size(48.dp))
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
                    Text(if (allSongs.isEmpty()) "暂无歌曲，请先扫描本地音乐" else "暂无歌曲",
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
                            onAddToPlaylist = { addToPlaylistSong = song },
                            onShowDetail = { detailSong = song },
                        )
                    }
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
        )
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            ),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
        cornerRadius = 14.dp,
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
