package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 底部功能栏 — 歌词/队列/收藏/更多
 */
@Composable
fun BottomFunctionBar(
    showLyrics: Boolean,
    isFavorite: Boolean,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomFuncButton(
            icon = MiuixIcons.NotesFill,
            label = "歌词",
            isActive = showLyrics,
            onClick = onLyricsClick,
        )
        BottomFuncButton(
            icon = MiuixIcons.Playlist,
            label = "队列",
            onClick = onQueueClick,
        )
        BottomFuncButton(
            icon = MiuixIcons.FavoritesFill, // miuix-icons 无空心版，收藏态由 isActive 高亮区分
            label = "收藏",
            isActive = isFavorite,
            onClick = onFavoriteClick,
        )
        BottomFuncButton(
            icon = MiuixIcons.MoreCircle,
            label = "更多",
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun BottomFuncButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    com.hezi.juyumao.ui.components.AnimatedIconButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                icon, label,
                tint = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                style = MiuixTheme.textStyles.footnote2,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
            )
        }
    }
}
