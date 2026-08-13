package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.ui.player.PlayerViewModel

/**
 * 自定义进度条 — Salt Player 风格
 * 细线条 + 圆形滑块 + 拖拽时放大
 * （Miuix Slider 不支持自定义 thumb/track，白色配色由 sliderColors 提供）
 * 进度数据每 200ms 更新：在组件内订阅，避免播放页整体高频重组。
 */
@Composable
fun PlayerSlider(
    viewModel: PlayerViewModel,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position by viewModel.position.collectAsStateWithLifecycle(initialValue = 0L)
    val duration by viewModel.duration.collectAsStateWithLifecycle(initialValue = 0L)
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

/** 毫秒 → m:ss（原私有实现已合并入 FormatUtils；此处零值回退 0:00 保持原行为） */
private fun formatDuration(ms: Long): String =
    com.hezi.juyumao.ui.theme.FormatUtils.formatDuration(ms, zeroText = "0:00")
