package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 自定义进度条 — Salt Player 风格
 * 细线条 + 圆形滑块 + 拖拽时放大
 * （Miuix Slider 不支持自定义 thumb/track，白色配色由 sliderColors 提供）
 */
@Composable
fun PlayerSlider(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val durationFloat = duration.toFloat().coerceAtLeast(1f)
    val sliderValue = if (isDragging) dragValue
        else position.toFloat().coerceIn(0f, durationFloat)

    Column(modifier = modifier) {
        Slider(
            value = sliderValue,
            onValueChange = { isDragging = true; dragValue = it },
            onValueChangeFinished = { isDragging = false; onSeek(dragValue.toLong()) },
            valueRange = 0f..durationFloat,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            colors = SliderDefaults.sliderColors(
                foregroundColor = MiuixTheme.colorScheme.onSurface,
                backgroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                thumbColor = MiuixTheme.colorScheme.onSurface,
            ),
        )

        // 时间文字
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(if (isDragging) dragValue.toLong() else position),
                style = MiuixTheme.textStyles.footnote2,
                color = Color.White.copy(alpha = 0.7f), // 深色艺术背景上的前景色
            )
            Text(
                formatDuration(duration),
                style = MiuixTheme.textStyles.footnote2,
                color = Color.White.copy(alpha = 0.7f), // 深色艺术背景上的前景色
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    // 指定 Locale，避免阿拉伯语等系统 locale 下输出非拉丁数字
    return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
