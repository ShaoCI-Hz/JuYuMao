package com.hezi.juyumao.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import java.io.File

/**
 * 歌曲列表项（全 App 统一格式，首页最近播放 / 曲库列表复用）：
 * 左封面 + 中歌名/歌手-专辑/音质·格式·时长/歌词(可选,10s 刷新)
 * + 右收藏(快捷) + 竖三点(聚合操作菜单，长按同菜单)
 */
@Composable
fun SongListItem(
    song: SongEntity,
    lyricLine: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLater: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowDetail: () -> Unit,
    onDownload: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
) {
    var showActionSheet by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = SinkFeedback(
                            sinkAmount = 0.85f,
                            animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                        ),
                        onClick = { onToggleSelect?.invoke() },
                    )
                } else {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = SinkFeedback(
                            sinkAmount = 0.85f,
                            animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                        ),
                        onClick = onClick,
                        onLongClick = { showActionSheet = true },
                    )
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 多选模式：行首勾选圈
        if (selectionMode) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "已选择",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .squircleBackground(
                                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                                cornerRadius = 11.dp,
                            ),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(20.dp)
                            .squircleBackground(
                                color = MiuixTheme.colorScheme.surfaceVariant,
                                cornerRadius = 10.dp,
                            ),
                    )
                }
            }
        }
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
                val ext = song.filePath.substringAfterLast('.', "").uppercase()
                if (ext.isNotBlank()) add(ext)
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
        // 右：收藏（快捷切换）+ 竖三点（聚合操作菜单）；多选模式隐藏
        if (!selectionMode) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallIconButton(
                    icon = MiuixIcons.FavoritesFill,
                    contentDescription = if (song.isFavorite) "取消收藏" else "收藏",
                    onClick = onToggleFavorite,
                    tint = if (song.isFavorite) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                SmallIconButton(MiuixIcons.More, "更多操作", onClick = { showActionSheet = true })
            }
        }
    }

    // 聚合操作菜单（三点 / 长按共用）
    if (!selectionMode) {
        SongActionSheet(
            song = song,
            show = showActionSheet,
            isFavorite = song.isFavorite,
            onDismiss = { showActionSheet = false },
            onPlay = { showActionSheet = false; onPlay() },
            onPlayNext = { showActionSheet = false; onPlayNext() },
            onPlayLater = { showActionSheet = false; onPlayLater() },
            onToggleFavorite = { showActionSheet = false; onToggleFavorite() },
            onAddToPlaylist = { showActionSheet = false; onAddToPlaylist() },
            onShowDetail = { showActionSheet = false; onShowDetail() },
            onDownload = onDownload?.let { d ->
                { showActionSheet = false; d() }
            },
        )
    }
}

/** 紧凑小图标按钮（32dp，带按压反馈） */
@Composable
private fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription,
            tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 歌曲详情弹窗：封面 + 歌名/歌手/专辑 + 星级评分 + 完整信息键值行 + 底部操作栏（可选回调） */
@Composable
fun SongDetailDialog(
    song: SongEntity,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onSetRating: ((Int) -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
) {
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
            // 星级评分（P2-13）
            if (onSetRating != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("评分", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= song.rating) Icons.Filled.Star
                                              else Icons.Outlined.StarBorder,
                                contentDescription = "$star 星",
                                tint = if (star <= song.rating) MiuixTheme.colorScheme.primary
                                       else MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = SinkFeedback(
                                            sinkAmount = 0.85f,
                                            animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                        ),
                                        onClick = {
                                            onSetRating(if (song.rating == star) 0 else star)
                                        },
                                    )
                                    .padding(2.dp),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.15f)))
            }
            // 本地歌曲：编辑标签入口（P2-14）
            if (onEditTags != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = SinkFeedback(
                                sinkAmount = 0.85f,
                                animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                            ),
                            onClick = onEditTags,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(MiuixIcons.Rename, null,
                        tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("编辑标签", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.primary)
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.15f)))
            }
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

            // 底部操作栏（有回调才显示对应按钮）
            if (onPlay != null || onToggleFavorite != null || onAddToPlaylist != null || onPlayNext != null) {
                Box(Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.15f)))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onPlay != null) DetailActionButton(MiuixIcons.Play, "播放", onPlay)
                    if (onToggleFavorite != null) {
                        DetailActionButton(
                            if (song.isFavorite) MiuixIcons.FavoritesFill else MiuixIcons.FavoritesFill,
                            if (song.isFavorite) "取消收藏" else "收藏",
                            onToggleFavorite,
                            tint = if (song.isFavorite) MiuixTheme.colorScheme.primary else null,
                        )
                    }
                    if (onAddToPlaylist != null) DetailActionButton(MiuixIcons.Add, "加歌单", onAddToPlaylist)
                    if (onPlayNext != null) DetailActionButton(MiuixIcons.ChevronForward, "下一首", onPlayNext)
                }
            }
        }
    }
}

/** 详情弹窗底部操作按钮：图标 + 小字，竖排 */
@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
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
            tint = tint ?: MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Text(label, style = MiuixTheme.textStyles.footnote2,
            color = tint ?: MiuixTheme.colorScheme.onSurfaceSecondary)
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
fun qualityLabel(song: SongEntity): String? {
    val ext = song.filePath.substringAfterLast('.', "").uppercase()
    val lossless = ext in setOf("FLAC", "WAV", "APE", "AIFF")
    if (lossless || (song.sampleRate >= 44100 && song.bitsPerSample >= 16)) return "SQ"
    if (song.bitrate >= 320_000) return "HQ"
    return null
}

/** 编辑标签对话框（P2-14）：仅本地歌曲，写文件后由调用方更新数据库 */
@Composable
fun EditTagsDialog(
    song: SongEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, year: Int) -> Unit,
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var yearText by remember { mutableStateOf(if (song.year > 0) song.year.toString() else "") }

    OverlayDialog(show = true, title = "编辑标签", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(value = title, onValueChange = { title = it }, label = "标题",
                modifier = Modifier.fillMaxWidth())
            TextField(value = artist, onValueChange = { artist = it }, label = "艺术家",
                modifier = Modifier.fillMaxWidth())
            TextField(value = album, onValueChange = { album = it }, label = "专辑",
                modifier = Modifier.fillMaxWidth())
            TextField(value = genre, onValueChange = { genre = it }, label = "流派",
                modifier = Modifier.fillMaxWidth())
            TextField(value = yearText, onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                label = "年份", modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                Button(
                    onClick = {
                        onSave(title, artist, album, genre, yearText.toIntOrNull() ?: 0)
                    },
                ) { Text("保存") }
            }
        }
    }
}
