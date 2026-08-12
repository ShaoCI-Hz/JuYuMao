package com.hezi.juyumao.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hezi.juyumao.ui.components.AnimatedIconButton

/**
 * 完整模式控制区 — 五按钮（随机/上一首/播放暂停/下一首/循环）
 * 全部使用 AnimatedIconButton 弹性按压反馈（T11.3）
 */
@Composable
fun FullControlRow(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 随机播放
        AnimatedIconButton(onClick = onShuffle) {
            Icon(Icons.Default.Shuffle, "随机",
                tint = if (shuffleEnabled) Color.White else Color.White.copy(alpha = 0.5f), // 深色艺术背景上的前景色
                modifier = Modifier.size(22.dp))
        }
        // 上一首
        AnimatedIconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, "上一首",
                tint = Color.White, // 深色艺术背景上的前景色
                modifier = Modifier.size(36.dp))
        }
        // 播放/暂停（大按钮）
        AnimatedIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, CircleShape), // 深色艺术背景上的白色圆钮
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.Black, // 白色圆钮内的高对比前景色
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        // 下一首
        AnimatedIconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, "下一首",
                tint = Color.White, // 深色艺术背景上的前景色
                modifier = Modifier.size(36.dp))
        }
        // 循环模式
        AnimatedIconButton(onClick = onRepeat) {
            Icon(
                imageVector = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "循环",
                tint = if (repeatMode > 0) Color.White else Color.White.copy(alpha = 0.5f), // 深色艺术背景上的前景色
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 沉浸模式控制区 — 三按钮（上一首/播放暂停/下一首）
 */
@Composable
fun ImmersiveControlRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedIconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipPrevious, "上一首",
                tint = Color.White.copy(alpha = 0.8f), // 深色艺术背景上的前景色
                modifier = Modifier.size(28.dp))
        }
        AnimatedIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape), // 深色艺术背景上的白色圆钮
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black, // 白色圆钮内的高对比前景色
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        AnimatedIconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipNext, "下一首",
                tint = Color.White.copy(alpha = 0.8f), // 深色艺术背景上的前景色
                modifier = Modifier.size(28.dp))
        }
    }
}
