package com.hezi.juyumao.ui.smartplaylist

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.data.local.db.entity.SmartPlaylistEntity
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.ui.components.SongDetailDialog
import com.hezi.juyumao.ui.components.SongListItem
import kotlin.random.Random
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback

@Composable
fun SmartPlaylistScreen(
    onBack: () -> Unit,
    onSongClick: (Long) -> Unit = {},
    viewModel: SmartPlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SmartPlaylistEntity?>(null) }

    // 歌词 10s tick（列表行歌词刷新）
    var lyricTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            lyricTick++
        }
    }
    // 列表行操作弹窗
    var addToPlaylistSong by remember { mutableStateOf<SongEntity?>(null) }
    var detailSong by remember { mutableStateOf<SongEntity?>(null) }

    if (current == null) {
        // ═══ 智能歌单列表页 ═══
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(
                title = "智能歌单",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
                },
                actions = {
                    IconButton(onClick = {
                        editing = null
                        showEditDialog = true
                    }) { Icon(MiuixIcons.Add, "新建") }
                },
            )
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(MiuixIcons.MindMap, null, tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(40.dp))
                        Text("还没有智能歌单", style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("按评分/播放次数/流派等条件自动筛选歌曲", style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playlists, key = { it.id }) { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                            cornerRadius = 12.dp,
                            pressFeedbackType = PressFeedbackType.Sink,
                            onClick = { viewModel.open(p) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(MiuixIcons.MindMap, null, tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurface, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(rulesSummary(p), style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                // 删除
                                IconButton(onClick = { viewModel.delete(p) }) {
                                    Icon(MiuixIcons.Delete, "删除",
                                        tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ═══ 智能歌单详情（歌曲列表）═══
        val playlist = current!!
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(
                title = playlist.name,
                navigationIcon = {
                    IconButton(onClick = { viewModel.close() }) { Icon(MiuixIcons.Back, "返回") }
                },
                actions = {
                    TextButton(text = "播放全部", onClick = { viewModel.playAll() })
                },
            )
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有符合条件的歌曲", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { _, song ->
                        SongListItem(
                            song = song,
                            lyricLine = null,
                            onClick = { onSongClick(song.id) },
                            onPlay = { onSongClick(song.id) },
                            onPlayNext = { viewModel.playNext(song) },
                            onPlayLater = { viewModel.playLater(song) },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            onAddToPlaylist = { addToPlaylistSong = song },
                            onShowDetail = { detailSong = song },
                        )
                    }
                }
            }
        }
    }

    // 编辑对话框（新建/编辑共用）
    if (showEditDialog) {
        SmartPlaylistEditDialog(
            initial = editing,
            onDismiss = { showEditDialog = false },
            onSave = { p, isNew ->
                viewModel.save(p, isNew)
                showEditDialog = false
            },
        )
    }

    // 列表行弹窗
    addToPlaylistSong?.let { song ->
        com.hezi.juyumao.ui.playlist.AddToPlaylistDialog(
            song = song,
            onDismiss = { addToPlaylistSong = null },
        )
    }
    detailSong?.let { song ->
        SongDetailDialog(
            song = song,
            onDismiss = { detailSong = null },
            onPlay = { detailSong = null; onSongClick(song.id) },
        )
    }
}

/** 规则摘要 */
private fun rulesSummary(p: SmartPlaylistEntity): String {
    val parts = mutableListOf<String>()
    if (p.minRating > 0) parts.add("评分≥${p.minRating}")
    if (p.minPlayCount > 0) parts.add("播放≥${p.minPlayCount}")
    if (p.addedWithinDays > 0) parts.add("${p.addedWithinDays}天内")
    p.genre?.takeIf { it.isNotBlank() }?.let { parts.add("流派:$it") }
    when (p.source) {
        "LOCAL" -> parts.add("本地")
        "SMB" -> parts.add("NAS")
    }
    if (p.isFavoriteOnly) parts.add("仅收藏")
    return if (parts.isEmpty()) "不限条件" else parts.joinToString(" · ")
}

/** 智能歌单编辑对话框 */
@Composable
private fun SmartPlaylistEditDialog(
    initial: SmartPlaylistEntity?,
    onDismiss: () -> Unit,
    onSave: (SmartPlaylistEntity, Boolean) -> Unit,
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var minRating by remember { mutableFloatStateOf(initial?.minRating?.toFloat() ?: 0f) }
    var minPlayCount by remember { mutableFloatStateOf(initial?.minPlayCount?.toFloat() ?: 0f) }
    var withinDays by remember { mutableFloatStateOf(initial?.addedWithinDays?.toFloat() ?: 0f) }
    var genre by remember { mutableStateOf(initial?.genre ?: "") }
    var source by remember { mutableStateOf(initial?.source ?: "") }
    var favOnly by remember { mutableStateOf(initial?.isFavoriteOnly ?: false) }

    OverlayDialog(show = true, title = if (isNew) "新建智能歌单" else "编辑智能歌单",
        onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(value = name, onValueChange = { name = it }, label = "名称",
                modifier = Modifier.fillMaxWidth())
            RuleSlider("最低评分", "minRating", minRating) { minRating = it }
            RuleSlider("最低播放次数", "minPlayCount", minPlayCount) { minPlayCount = it }
            RuleSlider("最近添加天数", "withinDays", withinDays) { withinDays = it }
            // 来源
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("来源", style = MiuixTheme.textStyles.body1)
                RadioButton(selected = source == "", onClick = { source = "" })
                Text("全部", style = MiuixTheme.textStyles.footnote1)
                RadioButton(selected = source == "LOCAL", onClick = { source = "LOCAL" })
                Text("本地", style = MiuixTheme.textStyles.footnote1)
                RadioButton(selected = source == "SMB", onClick = { source = "SMB" })
                Text("NAS", style = MiuixTheme.textStyles.footnote1)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("仅收藏", style = MiuixTheme.textStyles.body1)
                Switch(checked = favOnly, onCheckedChange = { favOnly = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(text = "取消", onClick = onDismiss)
                Button(onClick = {
                    val p = SmartPlaylistEntity(
                        id = initial?.id ?: 0,
                        name = name.trim().ifEmpty { "未命名歌单" },
                        minRating = minRating.toInt(),
                        minPlayCount = minPlayCount.toInt(),
                        addedWithinDays = withinDays.toInt(),
                        genre = genre.trim().ifEmpty { null },
                        source = source.ifEmpty { null },
                        isFavoriteOnly = favOnly,
                    )
                    onSave(p, isNew)
                }) { Text("保存") }
            }
        }
    }
}

/** 规则滑杆：标签 + 值 + Slider */
@Composable
private fun RuleSlider(label: String, unit: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MiuixTheme.textStyles.body1)
            Text("${value.toInt()} $unit", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = when (label) {
                "最低评分" -> 0f..5f
                "最低播放次数" -> 0f..100f
                else -> 0f..365f
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.sliderColors(
                thumbColor = MiuixTheme.colorScheme.primary,
                foregroundColor = MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
            ),
        )
    }
}
