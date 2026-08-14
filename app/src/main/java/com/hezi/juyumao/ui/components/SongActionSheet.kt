package com.hezi.juyumao.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hezi.juyumao.data.local.db.entity.SongEntity
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * 歌曲操作聚合底部弹层（OverlayBottomSheet）。
 * 全 App 统一歌曲操作入口：列表行三点/长按、播放页「更多」共用。
 * 操作按歌曲来源/上下文动态显示（NAS 歌有下载、播放页上下文有倍速）。
 */
@Composable
fun SongActionSheet(
    song: SongEntity,
    show: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLater: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowDetail: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onSetSpeed: (() -> Unit)? = null,
    onShareLyric: (() -> Unit)? = null,
    onPlaySimilar: (() -> Unit)? = null,
    showPlayActions: Boolean = true,
) {
    OverlayBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // 歌曲信息头：封面 + 歌名 + 歌手
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp).squircleBackground(
                        color = MiuixTheme.colorScheme.surfaceVariant, cornerRadius = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MiuixIcons.Music, null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(song.title, style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                }
            }
            // 分隔细线
            Box(Modifier.fillMaxWidth().height(0.5.dp)
                .background(MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.15f)))
            Spacer(modifier = Modifier.height(4.dp))

            if (showPlayActions) {
                SheetAction(MiuixIcons.Play, "播放", onPlay)
                SheetAction(MiuixIcons.ChevronForward, "下一首播放", onPlayNext)
                SheetAction(MiuixIcons.Playlist, "稍后播放", onPlayLater)
            }
            SheetAction(
                MiuixIcons.FavoritesFill,
                if (isFavorite) "取消收藏" else "收藏",
                onToggleFavorite,
                tint = if (isFavorite) MiuixTheme.colorScheme.primary else null,
            )
            SheetAction(MiuixIcons.Add, "添加到歌单", onAddToPlaylist)
            onDownload?.let { SheetAction(MiuixIcons.Download, "下载到本地", it) }
            onSetSpeed?.let { SheetAction(MiuixIcons.Timer, "倍速", it) }
            onShareLyric?.let { SheetAction(MiuixIcons.Share, "歌词海报", it) }
            onPlaySimilar?.let { SheetAction(MiuixIcons.Tune, "相似歌曲", it) }
            SheetAction(MiuixIcons.MoreCircle, "歌曲详情", onShowDetail)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 弹层操作行：图标 + 文字，整行可点击 */
@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null,
            tint = tint ?: MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp))
        Text(label, style = MiuixTheme.textStyles.body1,
            color = tint ?: MiuixTheme.colorScheme.onSurface)
    }
}
