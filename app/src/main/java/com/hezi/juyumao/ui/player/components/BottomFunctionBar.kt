package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
            icon = Icons.Default.Lyrics,
            label = "歌词",
            isActive = showLyrics,
            onClick = onLyricsClick,
        )
        BottomFuncButton(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = "队列",
            onClick = onQueueClick,
        )
        BottomFuncButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = "收藏",
            isActive = isFavorite,
            onClick = onFavoriteClick,
        )
        BottomFuncButton(
            icon = Icons.Default.MoreVert,
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
