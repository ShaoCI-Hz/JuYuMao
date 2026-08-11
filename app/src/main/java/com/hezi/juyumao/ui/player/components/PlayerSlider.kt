package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.*
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
                foregroundColor = Color.White,
                backgroundColor = Color.White.copy(alpha = 0.2f),
                thumbColor = Color.White,
            ),
        )

        // 时间文字
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(if (isDragging) dragValue.toLong() else position),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
