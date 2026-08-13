package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 播放页顶栏 — 返回 + 标题 + 沉浸模式切换
 */
@Composable
fun PlayerTopBar(
    onBack: () -> Unit,
    title: String,
    onImmersiveToggle: () -> Unit,
    isImmersive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                MiuixIcons.Back, "返回",
                tint = Color.White.copy(alpha = 0.9f),
            )
        }

        if (title.isNotEmpty()) {
            Text(
                title,
                style = MiuixTheme.textStyles.title4,
                color = Color.White.copy(alpha = 0.9f),
            )
        } else {
            Spacer(Modifier)
        }

        // 沉浸模式切换
        IconButton(onClick = onImmersiveToggle) {
            Icon(
                if (isImmersive) MiuixIcons.Show else MiuixIcons.Hide,
                "沉浸模式",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
