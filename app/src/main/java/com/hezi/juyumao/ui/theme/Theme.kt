package com.hezi.juyumao.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

/** 品牌扩展色：HiRes 金色徽标是功能标识，MIUI 配色下继续使用 */
data class ExtendedColors(
    val hiResGold: Color = HiResGold,
    val cardBackground: Color = CardDark,
)

private val DefaultExtendedColors = ExtendedColors()

val LocalExtendedColors = compositionLocalOf { DefaultExtendedColors }

/**
 * 主题：全盘 MIUI 原生配色 + Monet 动态取色（T4）。
 * - theme_mode 三模式映射到 Miuix ColorSchemeMode
 * - MonetSystem = 壁纸动态取色（无取色能力的设备回退 system 深/浅）
 * - 保留 LocalExtendedColors（HiRes 金色徽标）
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

    // theme_mode → Miuix ColorSchemeMode（system 模式用 MonetSystem 壁纸联动）
    val colorSchemeMode = when (themeMode) {
        ThemeMode.DARK -> ColorSchemeMode.MonetDark
        ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
    }

    val themeController = ThemeController(colorSchemeMode = colorSchemeMode)

    val extendedColors = ExtendedColors(
        hiResGold = HiResGold,
        cardBackground = if (isDark) CardDark else CardLight,
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
