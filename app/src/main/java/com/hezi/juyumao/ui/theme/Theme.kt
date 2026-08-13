package com.hezi.juyumao.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

/** 品牌扩展色：仅 HiRes 金色徽标（功能标识，MIUI 配色下继续使用） */
data class ExtendedColors(
    val hiResGold: Color = HiResGold,
)

private val DefaultExtendedColors = ExtendedColors()

val LocalExtendedColors = compositionLocalOf { DefaultExtendedColors }

/**
 * 主题：MIUI 风格（Miuix 默认配色）。
 * - ColorSchemeMode.Dark/Light/System → Miuix lightColorScheme(0xFF3482FF)/darkColorScheme(0xFF277AF7)
 *   鲜亮 MIUI 蓝 + 白/近黑/灰 层次（真机验证否决 Monet：其 MD3 tonal 变换产出低饱和灰蓝，非 MIUI 观感）。
 * - theme_mode 三模式（深/浅/跟随系统）映射 ColorSchemeMode。
 * - 保留 LocalExtendedColors（HiRes 金色徽标）。
 */
@Composable
fun JuYuMaoTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // theme_mode → Miuix ColorSchemeMode。真机验证结论：
    // Monet 模式（无论是否传 keyColor）本质是 MD3 算法——即使 seed 用 MIUI 蓝 0xFF3482FF，
    // 经 tonal 变换后 primary 变成低饱和灰蓝（真机实测 #707EA4），观感仍是 MD3。
    // 因此改用 Miuix 默认配色 ColorSchemeMode.Dark/Light/System：
    // primary 直接取 lightColorScheme(0xFF3482FF)/darkColorScheme(0xFF277AF7) 鲜亮 MIUI 蓝，
    // 背景/卡片为 MIUI 的 白/近黑/灰 层次。remember 避免每次重组重建控制器。
    val colorSchemeMode = when (themeMode) {
        ThemeMode.DARK -> ColorSchemeMode.Dark
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.SYSTEM -> ColorSchemeMode.System
    }
    val themeController = remember(colorSchemeMode) {
        ThemeController(colorSchemeMode = colorSchemeMode)
    }

    val extendedColors = ExtendedColors(
        hiResGold = HiResGold,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
    ) {
        MiuixTheme(
            controller = themeController,
            content = content,
        )
    }
}
