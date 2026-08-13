package com.hezi.juyumao.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.hezi.juyumao.ui.components.AnimatedIconButton
import java.io.File

@Composable
fun MiniPlayerBar(
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    songTitle: String? = null,
    songArtist: String? = null,
    artworkUri: String? = null,
    isPlaying: Boolean = false,
    progress: Float = 0f,
    onPlayPauseClick: () -> Unit = {},
) {
    // 进度动画：外部进度变化时平滑过渡
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 1f, stiffness = 120f),
        label = "mini_progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f),
            )
            .squircleBackground(
                color = MiuixTheme.colorScheme.surfaceVariant,
                cornerRadius = 16.dp,
            )
            .squircleClip(cornerRadius = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onPlayerClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 封面缩略图（切歌时 crossfade 过渡）
                AnimatedContent(
                    targetState = artworkUri,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "mini_artwork",
                ) { uri ->
                    if (uri != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(uri))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .squircleClip(cornerRadius = 8.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .squircleBackground(
                                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    cornerRadius = 8.dp,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Music,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // 歌曲信息（标题切歌 crossfade）
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    AnimatedContent(
                        targetState = songTitle,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "mini_title",
                    ) { title ->
                        Text(
                            text = title ?: "未在播放",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!songArtist.isNullOrEmpty()) {
                        Text(
                            text = songArtist,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 播放/暂停按钮（弹性按压）
                AnimatedIconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // 进度条（平滑动画）
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .squircleClip(cornerRadius = 1.dp),
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = MiuixTheme.colorScheme.primary,
                    backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
            )
        }
    }
}
