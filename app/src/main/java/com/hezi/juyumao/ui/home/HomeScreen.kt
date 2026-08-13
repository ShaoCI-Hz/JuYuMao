package com.hezi.juyumao.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import kotlin.random.Random
import kotlinx.coroutines.delay
import java.io.File

/**
 * 主页（v4.1 新排版）：
 * ┌─────────────────────────────────────┐
 * │ 局域猫播放器（紧贴状态栏，无多余空隙）    │
 * ├─────────────────────────────────────┤
 * │ 每日一言（紧凑单行）                    │
 * ├───────────────┬─────────────────────┤
 * │ 动态推荐专辑卡   │ 本地音乐             │
 * │ （封面+渐变+    ├─────────────────────┤
 * │  歌名/歌手/规格）│ NAS 连接             │
 * ├───────────────┴─────────────────────┤
 * │ 快捷操作（紧凑宫格 2 行 6 个）            │
 * ├─────────────────────────────────────┤
 * │ 最近播放（列表：封面+歌名+规格+HiRes+时长） │
 * └─────────────────────────────────────┘
 */
@Composable
fun HomeScreen(
    onNavigateToPlayer: (Long) -> Unit = {},
    onNavigateToSmb: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToQueue: () -> Unit = {},
    onNavigateToPlaylist: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onPlayAll: (List<SongEntity>) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val featuredSong by viewModel.featuredSong.collectAsStateWithLifecycle()
    var showSleepTimer by remember { mutableStateOf(false) }

    // 最近播放歌词每 10 秒随机刷新一句（全局 tick，各歌独立随机）
    var lyricTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            lyricTick++
        }
    }

    // 最近播放行的操作弹窗状态
    var addToPlaylistSong by remember { mutableStateOf<SongEntity?>(null) }
    var detailSong by remember { mutableStateOf<SongEntity?>(null) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.scanLocalMusic() }

    // 第一行固定高度 ≈ 屏幕高 1/5：推荐卡（左）与本地音乐/NAS 列（右）等高均分
    val featuredHeight = (LocalConfiguration.current.screenHeightDp / 5).dp

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题（紧贴状态栏）：Scaffold 已关闭系统栏 inset，此处由标题自行处理状态栏间距
        Text(
            text = "局域猫播放器",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // ═══ 每日一言（紧凑单行，紧跟标题）═══
            item {
                DailyGreetingCard(dailyCard = uiState.dailyCard)
            }

            // ═══ 第一行：动态推荐（左）+ 本地音乐 / NAS（右）═══
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(featuredHeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeaturedSongCard(
                        song = featuredSong,
                        onClick = { featuredSong?.let { onPlayAll(listOf(it)) } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CompactActionCard(
                            icon = {
                                Icon(Icons.Default.PhoneAndroid, null, // miuix-icons 无对应，保留 material icon
                                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            },
                            title = "本地音乐",
                            subtitle = if (uiState.isScanning) uiState.scanMessage
                                       else if (uiState.songCount > 0) "已收录 ${uiState.songCount} 首"
                                       else "点击扫描设备音乐",
                            subtitleColor = MiuixTheme.colorScheme.onSurfaceSecondary,
                            trailing = {
                                if (uiState.isScanning) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("扫描", style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary)
                                }
                            },
                            onClick = { permissionLauncher.launch(audioPermission) },
                            modifier = Modifier.weight(1f),
                        )
                        CompactActionCard(
                            icon = {
                                Icon(MiuixIcons.CloudFill, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            },
                            title = "NAS 连接",
                            subtitle = if (uiState.nasConnected) "已连接" else "未连接",
                            subtitleColor = if (uiState.nasConnected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                            trailing = {
                                Text(if (uiState.nasConnected) "管理" else "连接",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary)
                            },
                            onClick = onNavigateToSmb,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ═══ 快捷操作（紧凑宫格）═══
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text("快捷操作", style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionCard(MiuixIcons.Tune, "均衡器", onNavigateToEqualizer, Modifier.weight(1f)) // 原 Equalizer
                    QuickActionCard(MiuixIcons.Timer, "定时关闭", { showSleepTimer = true }, Modifier.weight(1f))
                    QuickActionCard(MiuixIcons.Playlist, "播放队列", onNavigateToQueue, Modifier.weight(1f)) // 原 QueueMusic
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionCard(MiuixIcons.Playlist, "歌单", onNavigateToPlaylist, Modifier.weight(1f)) // 原 QueueMusic
                    QuickActionCard(Icons.Default.BarChart, "听歌报告", onNavigateToStatistics, Modifier.weight(1f)) // miuix-icons 无对应，保留 material icon
                    QuickActionCard(MiuixIcons.Folder, "缓存管理", { }, Modifier.weight(1f)) // 原 Storage
                }
            }

            // ═══ 最近播放（列表形式，懒组合：滚动出屏自动回收）═══
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text("最近播放", style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(4.dp))
                if (uiState.recentlyPlayed.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        Text("暂无播放记录", style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
            }
            items(uiState.recentlyPlayed, key = { it.id }) { song ->
                // 每首歌歌词行：加载一次（key=song.id），每 10s tick 随机换一句
                val lyricLines by produceState<List<String>>(emptyList(), song.id) {
                    value = viewModel.loadLyricLines(song)
                }
                val lyricLine = remember(lyricLines, lyricTick) {
                    if (lyricLines.isEmpty()) null
                    else lyricLines[Random.nextInt(lyricLines.size)]
                }
                RecentSongListItem(
                    song = song,
                    lyricLine = lyricLine,
                    onClick = { onNavigateToPlayer(song.id) },
                    onAddToPlaylist = { addToPlaylistSong = song },
                    onShowDetail = { detailSong = song },
                )
            }
        }
    }

    if (showSleepTimer) {
        com.hezi.juyumao.ui.sleep.SleepTimerSheet(
            onDismiss = { showSleepTimer = false },
        )
    }

    // 最近播放行：添加到歌单弹窗
    addToPlaylistSong?.let { song ->
        com.hezi.juyumao.ui.playlist.AddToPlaylistDialog(
            song = song,
            onDismiss = { addToPlaylistSong = null },
        )
    }
    // 最近播放行：歌曲详情弹窗
    detailSong?.let { song ->
        SongDetailDialog(
            song = song,
            onDismiss = { detailSong = null },
        )
    }
}

/**
 * 动态推荐专辑卡：约 ½ 屏宽 × 近 1:1 高。
 * 封面铺满 + 下半部渐变 scrim + 歌名/歌手/规格 三行白字；点击直接播放该曲。
 * 歌曲由 HomeViewModel 随机推荐（启动一次 + 每 10 秒切换），AnimatedContent 交叉淡入。
 */
@Composable
private fun FeaturedSongCard(
    song: SongEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            ),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
        cornerRadius = 16.dp,
    ) {
        AnimatedContent(
            targetState = song,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "featured_song",
        ) { current ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 封面专辑图（无封面时渐变占位 + 音符图标）
                if (!current?.albumArtUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = File(current.albumArtUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().squircleClip(cornerRadius = 16.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .squircleBackground(
                                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                                cornerRadius = 16.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MiuixIcons.Music, null,
                            tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp))
                    }
                }
                // 下半部渐变 scrim（保证白字可读）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .squircleClip(cornerRadius = 16.dp)
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f),
                            )
                        ),
                )
                // 文字区：歌名 / 歌手 / 音频规格
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = current?.title ?: "每日推荐",
                        style = MiuixTheme.textStyles.body1,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current?.artist?.takeIf { it.isNotBlank() && it != "未知艺术家" } ?: "点击播放",
                        style = MiuixTheme.textStyles.footnote1,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    current?.let { s ->
                        val specs = buildList {
                            if (s.sampleRate > 0) add("${s.sampleRate / 1000.0}kHz".replace(".0", ""))
                            if (s.bitsPerSample > 0) add("${s.bitsPerSample}bit")
                            if (s.bitrate > 0) add("${(s.bitrate / 1000).coerceAtLeast(1)}kbps")
                        }
                        if (specs.isNotEmpty()) {
                            Text(
                                text = specs.joinToString(" · "),
                                style = MiuixTheme.textStyles.footnote2,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 紧凑功能卡（右半列：本地音乐 / NAS 连接）：图标 + 标题 + 副标题 + 尾随状态，整卡可点击 */
@Composable
private fun CompactActionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = SinkFeedback(
                sinkAmount = 0.85f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.99f, stiffness = 986.96f),
            ),
            onClick = onClick,
        ),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MiuixTheme.textStyles.footnote1,
                    color = subtitleColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

/** 每日一言（紧凑单行）：问候 + 引用，无天气；紧跟标题下方 */
@Composable
private fun DailyGreetingCard(dailyCard: DailyCardData) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)),
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.AutoAwesome, null, // miuix-icons 无对应，保留 material icon
                tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text("${dailyCard.greeting}，${dailyCard.dateText}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1)
            Spacer(modifier = Modifier.width(4.dp))
            Text(dailyCard.quote,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
    }
}

/** 最近播放列表项：左封面 + 中歌名/歌手-专辑/音质·格式·时长/歌词 + 右加号/竖三点 */
@Composable
private fun RecentSongListItem(
    song: SongEntity,
    lyricLine: String?,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowDetail: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左：封面缩略图（52dp）
        if (!song.albumArtUri.isNullOrEmpty()) {
            AsyncImage(model = File(song.albumArtUri), contentDescription = null,
                modifier = Modifier.size(52.dp).squircleClip(cornerRadius = 8.dp),
                contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.size(52.dp)
                .squircleBackground(color = MiuixTheme.colorScheme.primary.copy(0.1f), cornerRadius = 8.dp),
                contentAlignment = Alignment.Center) {
                Icon(MiuixIcons.Music, null,
                    tint = MiuixTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(24.dp))
            }
        }
        // 中：歌名 + 歌手-专辑 + 音质·格式·时长 + 歌词
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.title, style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 歌手 - 专辑（未知的跳过，避免"未知艺术家 - 未知专辑"）
            val artistAlbum = buildString {
                if (song.artist.isNotBlank() && song.artist != SongEntity.UNKNOWN_ARTIST) append(song.artist)
                if (song.album.isNotBlank() && song.album != SongEntity.UNKNOWN_ALBUM) {
                    if (isNotEmpty()) append(" - ")
                    append(song.album)
                }
            }
            if (artistAlbum.isNotEmpty()) {
                Text(artistAlbum, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 第三行：音质标识 · 文件格式 · 歌曲时长
            val info = buildList {
                qualityLabel(song)?.let { add(it) }
                add(song.filePath.substringAfterLast('.', "").uppercase())
                add(com.hezi.juyumao.ui.theme.FormatUtils.formatDuration(song.duration))
            }
            Text(info.joinToString(" · "), style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 第四行：随机歌词（每 10 秒刷新一句，无歌词则不占位）
            if (lyricLine != null) {
                Text("♪ $lyricLine", style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // 右：加号（添加到歌单）+ 竖三点（歌曲详情）
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallIconButton(MiuixIcons.Add, "添加到歌单", onAddToPlaylist)
            SmallIconButton(Icons.Default.MoreVert, "歌曲详情", onShowDetail) // miuix-icons 无竖三点，保留 material icon
        }
    }
}

/** 紧凑小图标按钮（32dp，带按压反馈） */
@Composable
private fun SmallIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription,
            tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(20.dp))
    }
}

/** 歌曲详情弹窗：封面 + 歌名/歌手/专辑 + 完整信息键值行 */
@Composable
private fun SongDetailDialog(song: SongEntity, onDismiss: () -> Unit) {
    OverlayDialog(show = true, title = "歌曲详情", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 封面 + 歌名/歌手/专辑
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!song.albumArtUri.isNullOrEmpty()) {
                    AsyncImage(model = File(song.albumArtUri), contentDescription = null,
                        modifier = Modifier.size(64.dp).squircleClip(cornerRadius = 12.dp),
                        contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.size(64.dp)
                        .squircleBackground(color = MiuixTheme.colorScheme.primary.copy(0.1f), cornerRadius = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Icon(MiuixIcons.Music, null,
                            tint = MiuixTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(30.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(song.title, style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                    Text(song.album, style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                }
            }
            // 分隔细线
            Box(Modifier.fillMaxWidth().height(0.5.dp)
                .background(MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.15f)))
            // 信息键值行
            DetailRow("格式", song.filePath.substringAfterLast('.', "").uppercase())
            DetailRow("音质", qualityLabel(song) ?: "标准")
            DetailRow("时长", com.hezi.juyumao.ui.theme.FormatUtils.formatDuration(song.duration))
            if (song.sampleRate > 0) DetailRow("采样率", "${song.sampleRate / 1000.0}kHz".replace(".0", ""))
            if (song.bitsPerSample > 0) DetailRow("位深", "${song.bitsPerSample}bit")
            if (song.bitrate > 0) DetailRow("码率", "${(song.bitrate / 1000).coerceAtLeast(1)}kbps")
            if (song.fileSize > 0) DetailRow("大小", com.hezi.juyumao.ui.theme.FormatUtils.formatFileSize(song.fileSize))
            DetailRow("来源", if (song.source == "LOCAL") "本地音乐" else "NAS")
            DetailRow("路径", song.filePath)
        }
    }
}

/** 详情键值行：左标签右值 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/**
 * 音质标识：无损格式（FLAC/WAV/APE/AIFF）或高规格（采样率≥44.1k 且位深≥16）→ SQ；
 * 码率 ≥ 320kbps 的有损 → HQ；其余 → null（不显示）
 */
private fun qualityLabel(song: SongEntity): String? {
    val ext = song.filePath.substringAfterLast('.', "").uppercase()
    val lossless = ext in setOf("FLAC", "WAV", "APE", "AIFF")
    if (lossless || (song.sampleRate >= 44100 && song.bitsPerSample >= 16)) return "SQ"
    if (song.bitrate >= 320_000) return "HQ"
    return null
}

/** 快捷操作（紧凑宫格）：小图标 + 小字，垂直居中 */
@Composable
private fun QuickActionCard(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = SinkFeedback(
                sinkAmount = 0.85f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.99f, stiffness = 986.96f),
            ),
            onClick = onClick,
        ),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
        cornerRadius = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
