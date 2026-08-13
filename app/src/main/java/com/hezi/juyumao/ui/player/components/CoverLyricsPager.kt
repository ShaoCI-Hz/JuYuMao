package com.hezi.juyumao.ui.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.hezi.juyumao.player.audio.LyricsData
import com.hezi.juyumao.ui.lyrics.LyricsView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun CoverLyricsPager(
    artworkUri: String?,
    lyricsData: LyricsData?,
    positionFlow: Flow<Long>,
    isPlaying: Boolean,
    showLyrics: Boolean = false,
    lyricsFontSize: Float = 18f,
    lyricsFontBold: Boolean = true,
    onLineClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    // 当外部 showLyrics 变化时，切换到歌词页（直接在 LaunchedEffect 内挂起，
    // effect 重启/取消时动画自动取消，避免快速连点叠加多个滚动协程）
    LaunchedEffect(showLyrics) {
        if (showLyrics && pagerState.currentPage == 0) {
            pagerState.animateScrollToPage(1)
        } else if (!showLyrics && pagerState.currentPage == 1) {
            pagerState.animateScrollToPage(0)
        }
    }

    // 切歌封面过渡：artworkUri 变化时淡入
    val coverAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "cover_fade",
    )

    Column(modifier = modifier) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            // 翻页拖拽跟随：目标页随 offset 缩放 + 透明度渐变（T11.5）
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
            val scale = 1f - kotlin.math.abs(pageOffset) * 0.08f
            val alpha = 1f - kotlin.math.abs(pageOffset) * 0.3f

            when (page) {
                0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // 封面呼吸光晕（随播放脉动，T11.3）
                        com.hezi.juyumao.ui.components.PulsingGlow(
                            color = Color.White.copy(alpha = 0.25f),
                            size = 380.dp,
                            active = isPlaying,
                        )
                        AlbumArtPager(
                            artworkUri = artworkUri,
                            isPlaying = isPlaying,
                            isRound = false,
                            size = 280.dp,
                            modifier = Modifier.graphicsLayer { this.alpha = coverAlpha },
                        )
                    }
                }
                1 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                    ) {
                        LyricsView(
                            lyricsData = lyricsData,
                            positionFlow = positionFlow,
                            fontSize = lyricsFontSize,
                            fontBold = lyricsFontBold,
                            onLineClick = onLineClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // 页面指示器
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .background(
                            color = if (pagerState.currentPage == index)
                                Color.White else Color.White.copy(alpha = 0.4f),
                            shape = CircleShape,
                        )
                )
            }
        }
    }
}
