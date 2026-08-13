package com.hezi.juyumao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hezi.juyumao.ui.navigation.BottomNavItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

/**
 * 底部导航栏：Miuix NavigationBar（MIUI 原生底栏，替代原自绘悬浮胶囊）。
 * 选中态颜色由 Miuix NavigationBarItem 按 MiuixTheme 处理（primary），无需手动 tint。
 */
@Composable
fun PremiumBottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            val isSelected = item.screen.route == currentRoute
            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemSelected(item) },
                icon = if (isSelected) item.selectedIcon else item.unselectedIcon,
                label = item.label,
            )
        }
    }
}
