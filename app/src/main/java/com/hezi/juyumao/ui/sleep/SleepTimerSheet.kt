package com.hezi.juyumao.ui.sleep

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class TimerOption(val label: String, val minutes: Int)

@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit,
    viewModel: SleepTimerViewModel = hiltViewModel(),
) {
    val options = listOf(
        TimerOption("15 分钟", 15),
        TimerOption("30 分钟", 30),
        TimerOption("45 分钟", 45),
        TimerOption("60 分钟", 60),
        TimerOption("90 分钟", 90),
    )

    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val isTimerRunning = remaining > 0

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        cornerRadius = 28.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = MiuixIcons.Timer,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "定时关闭",
                style = MiuixTheme.textStyles.title4,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isTimerRunning) {
                // 倒计时显示
                val minutes = remaining / 60
                val seconds = remaining % 60
                Text(
                    text = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.cancelTimer() },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                    ),
                    cornerRadius = 12.dp,
                ) {
                    Text("取消定时")
                }
            } else {
                // 选项列表
                options.forEach { option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = SinkFeedback(
                                    sinkAmount = 0.85f,
                                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                ),
                            ) { viewModel.setTimer(option.minutes) },
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceVariant,
                        ),
                        cornerRadius = 12.dp,
                    ) {
                        Text(
                            text = option.label,
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
