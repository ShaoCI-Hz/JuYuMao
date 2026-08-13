package com.hezi.juyumao.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hezi.juyumao.ui.navigation.BottomNavItem
import com.hezi.juyumao.ui.navigation.Screen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * 悬浮胶囊底栏（替代原"模块底栏 + 迷你播放条"双底栏）。
 * 4 项：首页 / 目录 / 播放 / 设置。正圆胶囊圆角（RoundedCornerShape(50)），
 * 悬浮于屏幕底部上方（左右 24dp、上下 12dp 留白），带投影阴影。
 * 选中项图标+文字用主题 primary 高亮；播放项选中态匹配 player/ 开头的路由。
 */
@Composable
fun FloatingNavBar(
    items: List<BottomNavItem>,
    currentRoute: String,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 悬浮在手势导航条上方（系统已关闭 Scaffold 的 inset，由底栏自行处理）
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(color = MiuixTheme.colorScheme.background, shape = shape),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = when (item.screen) {
                    is Screen.Player -> currentRoute.startsWith("player/")
                    else -> item.screen.route == currentRoute
                }
                FloatingNavItem(
                    // RowScope 内可用 weight
                    modifier = Modifier.weight(1f),
                    icon = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    label = item.label,
                    selected = isSelected,
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceSecondary
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.9f,
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(0.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
