package com.hezi.juyumao.ui.browse

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.data.local.db.entity.SongEntity

@Composable
fun BrowseScreen(
    onSongClick: (Long) -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val albumNames by viewModel.albumNames.collectAsStateWithLifecycle()
    val artistNames by viewModel.artistNames.collectAsStateWithLifecycle()
    val genreNames by viewModel.genreNames.collectAsStateWithLifecycle()
    val dimensionSongs by viewModel.dimensionSongs.collectAsStateWithLifecycle()
    val batchState by viewModel.batchCacheState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    // 维度浏览状态：-1=维度列表，否则为选中维度对应的歌曲列表
    var dimensionType by remember { mutableStateOf("album") }
    var dimensionName by remember { mutableStateOf<String?>(null) }

    val filteredSongs by remember {
        derivedStateOf {
            when (selectedTab) {
                1 -> allSongs.filter { it.source == "LOCAL" }
                2 -> allSongs.filter { it.source == "SMB" }
                3 -> favorites
                4 -> dimensionSongs
                else -> allSongs
            }
        }
    }

    val localCount by remember { derivedStateOf { allSongs.count { it.source == "LOCAL" } } }
    val smbCount by remember { derivedStateOf { allSongs.count { it.source == "SMB" } } }
    val favoriteCount by remember { derivedStateOf { favorites.size } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = "浏览")

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs（Miuix TabRow 可横向滚动，避免窄屏挤压）
        val tabLabels = listOf(
            "全部 ${allSongs.size}",
            "本地 $localCount",
            "NAS $smbCount",
            "我喜欢 $favoriteCount",
            "维度",
        )
        TabRow(
            tabs = tabLabels,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 批量缓存进度条
        if (batchState.isRunning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
                cornerRadius = 10.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "正在缓存歌曲元数据... (${batchState.processed}/${batchState.total})",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                    if (batchState.currentSongTitle.isNotEmpty()) {
                        Text(
                            text = batchState.currentSongTitle,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LinearProgressIndicator(
                        progress = batchState.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 维度浏览（专辑/艺术家/流派）
        if (selectedTab == 4) {
            DimensionBrowse(
                albumNames = albumNames,
                artistNames = artistNames,
                genreNames = genreNames,
                dimensionSongs = dimensionSongs,
                dimensionType = dimensionType,
                dimensionName = dimensionName,
                onTypeChange = { type ->
                    dimensionType = type
                    dimensionName = null
                },
                onSelectDimension = { name ->
                    dimensionName = name
                    viewModel.loadDimensionSongs(dimensionType, name)
                },
                onBackToList = { dimensionName = null },
                onSongClick = onSongClick,
            )
        } else if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Music, // 原 MusicOff，miuix-icons 无对应，改用 Music
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = if (allSongs.isEmpty()) "暂无歌曲，请先扫描本地音乐"
                               else "当前筛选无结果",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                items(
                    items = filteredSongs,
                    key = { it.id },
                ) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song.id) },
                        onEnsureArtwork = { viewModel.ensureArtwork(song) },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        isProcessing = batchState.isRunning && batchState.currentSongId == song.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongListItem(
    song: SongEntity,
    onClick: () -> Unit,
    onEnsureArtwork: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    isProcessing: Boolean = false,
) {
    // 列表项显示时按需提取 NAS 封面
    LaunchedEffect(song.id, song.albumArtUri) {
        if (song.albumArtUri.isNullOrEmpty()) onEnsureArtwork()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 缩略图（有封面显示封面，无封面显示音符图标）
        if (!song.albumArtUri.isNullOrEmpty()) {
            coil.compose.AsyncImage(
                model = java.io.File(song.albumArtUri),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .squircleClip(cornerRadius = 8.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                        cornerRadius = 8.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Music, // 原 MusicNote
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = song.title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Hi-Res / DSD 徽标（金色）
                if (song.isHiRes) {
                    val isDsd = song.filePath.substringAfterLast('.', "").lowercase() in setOf("dsf", "dff")
                    HiResBadge(text = if (isDsd) "DSD" else "Hi-Res")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 来源标签
                val (tagText, tagColor) = if (song.source == "LOCAL") {
                    "本地" to MiuixTheme.colorScheme.primary
                } else {
                    "NAS" to MiuixTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .squircleBackground(
                            color = tagColor.copy(alpha = 0.15f),
                            cornerRadius = 4.dp,
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = tagText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = tagColor,
                    )
                }
                Text(
                    text = "${song.artist.ifEmpty { "未知艺术家" }} · ${song.album.ifEmpty { "未知专辑" }}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 时长或处理中指示
        if (isProcessing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("缓存中", style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(song.duration),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                // 收藏按钮
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = MiuixIcons.FavoritesFill, // 原 Favorite/FavoriteBorder（未收藏时用 tint 半透明区分）
                        contentDescription = if (song.isFavorite) "取消收藏" else "收藏",
                        tint = if (song.isFavorite) MiuixTheme.colorScheme.primary
                               else MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** 金色 Hi-Res / DSD 徽标 */
@Composable
private fun HiResBadge(text: String) {
    Box(
        modifier = Modifier
            .squircleBackground(
                color = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold.copy(alpha = 0.15f),
                cornerRadius = 4.dp,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 维度浏览：专辑/艺术家/流派 三级（列表 → 详情歌曲） */
@Composable
private fun DimensionBrowse(
    albumNames: List<String>,
    artistNames: List<String>,
    genreNames: List<String>,
    dimensionSongs: List<SongEntity>,
    dimensionType: String,
    dimensionName: String?,
    onTypeChange: (String) -> Unit,
    onSelectDimension: (String) -> Unit,
    onBackToList: () -> Unit,
    onSongClick: (Long) -> Unit,
) {
    // 维度类型切换
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("album" to "专辑", "artist" to "艺术家", "genre" to "流派").forEach { (type, label) ->
            MiuixFilterChip(
                selected = dimensionType == type,
                onClick = { onTypeChange(type) },
                label = label,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (dimensionName == null) {
        // 维度名称列表
        val names = when (dimensionType) {
            "artist" -> artistNames
            "genre" -> genreNames
            else -> albumNames
        }
        if (names.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无数据", style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                items(names, key = { it }) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                                onClick = { onSelectDimension(name) },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = when (dimensionType) {
                                "artist" -> MiuixIcons.Contacts // 原 Person
                                "genre" -> MiuixIcons.Music // 原 MusicNote
                                else -> MiuixIcons.Album
                            },
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(name, style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(MiuixIcons.ChevronForward, null, // 原 KeyboardArrowRight
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    } else {
        // 维度歌曲列表
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(
                title = dimensionName,
                subtitle = "${dimensionSongs.size} 首",
                navigationIcon = {
                    IconButton(onClick = onBackToList) {
                        Icon(MiuixIcons.Back, "返回")
                    }
                },
                defaultWindowInsetsPadding = false,
            )
            if (dimensionSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无歌曲", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                    items(dimensionSongs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                                    onClick = { onSongClick(song.id) },
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!song.albumArtUri.isNullOrEmpty()) {
                                coil.compose.AsyncImage(
                                    model = java.io.File(song.albumArtUri),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).squircleClip(cornerRadius = 6.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            } else {
                                Icon(MiuixIcons.Music, null, // 原 MusicNote
                                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                text = formatDuration(song.duration),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Miuix 风格的筛选 Chip（替代 M3 FilterChip） */
@Composable
private fun MiuixFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}
