package com.hezi.juyumao.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun RotatingAlbumArt(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    artworkUri: String? = null,
) {
    // 仅在播放时启动无限旋转动画，暂停时不消耗 GPU
    val infiniteTransition = if (isPlaying) {
        rememberInfiniteTransition(label = "album_rotation")
    } else {
        null
    }
    val rotation by if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "album_rotation",
        )
    } else {
        // 暂停时固定 0，避免无限动画
        val zero = remember { mutableFloatStateOf(0f) }
        zero
    }

    // 记住暂停时的旋转角度
    var pausedRotation by remember { mutableFloatStateOf(0f) }
    // 修复已知 bug：key 原为 (isPlaying, rotation)，而 rotation 在播放时每帧变化，
    // 导致 LaunchedEffect 每帧取消并重启。改为仅以 isPlaying 为 key 一次启动，
    // 播放时在 withInfiniteAnimationFrameMillis 内每帧记录角度（暂停时自动停止）。
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            withInfiniteAnimationFrameMillis {
                pausedRotation = rotation
            }
        }
    }
    val displayRotation by animateFloatAsState(
        targetValue = if (isPlaying) rotation else pausedRotation,
        animationSpec = tween(durationMillis = 300),
        label = "album_display_rotation",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = displayRotation
            }
            .shadow(elevation = 20.dp, shape = CircleShape)
            .background(
                color = MiuixTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(artworkUri))
                    .crossfade(true)
                    .build(),
                contentDescription = "专辑封面",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "专辑封面",
                tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}
