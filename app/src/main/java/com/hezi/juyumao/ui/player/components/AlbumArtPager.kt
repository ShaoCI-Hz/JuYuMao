package com.hezi.juyumao.ui.player.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun AlbumArtPager(
    artworkUri: String?,
    isPlaying: Boolean,
    isRound: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val shape = if (isRound) CircleShape else RoundedCornerShape(16.dp)

    // 旋转动画（仅圆形唱片模式时创建）
    val rotation = if (isRound) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotate")
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rotation",
        ).value
    } else 0f

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (isRound && isPlaying) rotationZ = rotation
            }
            .shadow(24.dp, shape)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = File(artworkUri),
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MiuixTheme.colorScheme.secondary.copy(alpha = 0.3f),
                        )
                    )
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MiuixIcons.Music, null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}
