package com.hezi.juyumao.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import java.io.File

/**
 * 歌曲列表项（全 App 统一格式，首页最近播放 / 曲库列表复用）：
 * 左封面 + 中歌名/歌手-专辑/音质·格式·时长/歌词(可选,10s 刷新) + 右加号(加歌单)/竖三点(详情)
 */
@Composable
fun SongListItem(
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
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
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
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
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
fun SongDetailDialog(song: SongEntity, onDismiss: () -> Unit) {
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
fun qualityLabel(song: SongEntity): String? {
    val ext = song.filePath.substringAfterLast('.', "").uppercase()
    val lossless = ext in setOf("FLAC", "WAV", "APE", "AIFF")
    if (lossless || (song.sampleRate >= 44100 && song.bitsPerSample >= 16)) return "SQ"
    if (song.bitrate >= 320_000) return "HQ"
    return null
}
