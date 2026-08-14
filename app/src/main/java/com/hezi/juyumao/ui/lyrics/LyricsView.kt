package com.hezi.juyumao.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hezi.juyumao.player.audio.LyricsData
import com.hezi.juyumao.player.audio.LrcParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyricsData: LyricsData?,
    positionFlow: Flow<Long>,
    modifier: Modifier = Modifier,
    fontSize: Float = 18f,
    fontBold: Boolean = true,
    onLineClick: ((Long) -> Unit)? = null,
) {
    // 播放进度订阅（歌词高亮需要）：只在此组件内读取，避免播放页因 200ms 进度更新整体重组；
    // 纯文本歌词（无时间戳）或空歌词时不订阅，零重组开销
    val hasTimestamps = lyricsData?.lines?.any { it.timeMs > 0 } == true
    val currentPositionMs by produceState(0L, positionFlow, hasTimestamps) {
        if (hasTimestamps) {
            positionFlow.collect { value = it }
        }
    }
    if (lyricsData == null || lyricsData.lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f))
        }
        return
    }

    // 纯文本歌词（全部行 timeMs == 0）不计算高亮行：findCurrentLineIndex 对 positionMs >= 0
    // 恒返回最后一行，会让纯文本歌词永远高亮末行并滚到底部
    val currentIndex = remember(lyricsData, currentPositionMs) {
        if (!hasTimestamps) -1
        else LrcParser.findCurrentLineIndex(lyricsData.lines, currentPositionMs)
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 用户手动滚动时暂停自动滚动，5秒无操作后恢复
    var autoScrollEnabled by remember { mutableStateOf(true) }
    // 程序自动滚动标志：区分 animateScrollToItem 与用户手势（否则自动滚动自身会禁用自己）
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            // 只有用户手势才禁用自动滚动（程序滚动由 autoScrolling 标记忽略）
            if (!autoScrolling) autoScrollEnabled = false
        } else {
            delay(5000)
            autoScrollEnabled = true
        }
    }

    // 自动滚动到当前行（仅当用户未手动滚动时；动画不打断用户操作）
    LaunchedEffect(currentIndex, autoScrollEnabled) {
        if (currentIndex >= 0 && autoScrollEnabled && !listState.isScrollInProgress) {
            autoScrolling = true
            try {
                listState.animateScrollToItem(
                    index = maxOf(0, currentIndex - 3),
                    scrollOffset = 0,
                )
            } finally {
                autoScrolling = false
            }
        }
    }

    // 切歌时滚回顶部（listState 无 key，切歌后停留在上一首歌的滚动位置）
    LaunchedEffect(lyricsData) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 120.dp, bottom = 200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyricsData.lines) { index, line ->
            val isCurrent = index == currentIndex
            val distance = kotlin.math.abs(index - currentIndex)

            // 当 currentIndex < 0（尚未播放到任何歌词行）时，所有行正常显示
            // 当 currentIndex >= 0 时，距离越远越透明
            val targetAlpha = when {
                currentIndex < 0 -> 0.6f          // 未开始播放，全部半透明
                isCurrent -> 1f                    // 当前行完全不透明
                distance <= 2 -> 0.6f - (distance - 1) * 0.15f  // 相邻行渐变
                else -> 0.25f                      // 远处行低透明度
            }

            val targetScale = when {
                currentIndex < 0 -> 1f
                isCurrent -> 1.08f
                else -> 0.95f
            }

            val targetFontSize = when {
                currentIndex < 0 -> fontSize
                isCurrent -> fontSize + 4f
                else -> fontSize - 2f
            }

            val targetFontWeight = when {
                currentIndex < 0 -> FontWeight.Normal
                isCurrent && fontBold -> FontWeight.Bold
                distance <= 1 -> FontWeight.Medium
                else -> FontWeight.Normal
            }

            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(300),
                label = "lyric_alpha",
            )

            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
                label = "lyric_scale",
            )

            val animatedFontSize by animateFloatAsState(
                targetValue = targetFontSize,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f),
                label = "lyric_font_size",
            )

            val color by animateColorAsState(
                targetValue = when {
                    currentIndex < 0 -> MiuixTheme.colorScheme.onSurfaceSecondary
                    isCurrent -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceSecondary
                },
                animationSpec = tween(300),
                label = "lyric_color",
            )

            // 逐字卡拉OK 高亮：当前行且含逐字时间时，已唱字用主题色、未唱字半透明
            val lyricPrimary = MiuixTheme.colorScheme.primary
            val lyricSecondary = MiuixTheme.colorScheme.onSurfaceSecondary
            val annotated = remember(line, currentPositionMs, lyricPrimary, lyricSecondary) {
                if (isCurrent && !line.words.isNullOrEmpty()) {
                    buildAnnotatedString {
                        line.words.forEach { w ->
                            val wordColor = if (currentPositionMs >= w.timeMs)
                                lyricPrimary
                            else
                                lyricSecondary.copy(alpha = 0.7f)
                            withStyle(SpanStyle(color = wordColor)) {
                                append(w.text)
                            }
                        }
                    }
                } else null
            }

            Text(
                text = annotated ?: androidx.compose.ui.text.AnnotatedString(line.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = SinkFeedback(
                            sinkAmount = 0.85f,
                            animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                        ),
                        enabled = onLineClick != null && line.timeMs > 0,
                    ) {
                        onLineClick?.invoke(line.timeMs)
                    }
                    .padding(horizontal = 28.dp, vertical = if (isCurrent) 10.dp else 5.dp),
                fontSize = animatedFontSize.sp,
                fontWeight = targetFontWeight,
                color = color,
                textAlign = TextAlign.Center,
                lineHeight = (animatedFontSize * 1.5f).sp,
            )
        }
    }
}
