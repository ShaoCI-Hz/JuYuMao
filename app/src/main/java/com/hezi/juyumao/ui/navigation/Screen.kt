package com.hezi.juyumao.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Browse : Screen("browse")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object Player : Screen("player/{songId}") {
        fun createRoute(songId: Long) = "player/$songId"
    }
    data object Queue : Screen("queue")
    data object SmbConnect : Screen("smb_connect?guide={guide}") {
        fun createRoute(guide: Boolean = false) = "smb_connect?guide=$guide"
    }
    data object Equalizer : Screen("equalizer")
    data object Cache : Screen("cache")
    data object Playlist : Screen("playlist")
    data object SmartPlaylist : Screen("smart_playlist")
    data object Statistics : Screen("statistics")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

// 悬浮底栏 4 项：首页 / 目录（浏览）/ 播放 / 设置（搜索入口移至目录页顶栏）。
// 图标换 MiuixIcons（MIUI 线性图标）；miuix 无双态图标，Home/Play/Settings 选中未选共用，
// 目录用 FolderFill/Folder 区分选中态。
val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "首页", MiuixIcons.Home, MiuixIcons.Home),
    BottomNavItem(Screen.Browse, "曲库", MiuixIcons.FolderFill, MiuixIcons.Folder),
    BottomNavItem(Screen.Player, "播放", MiuixIcons.Play, MiuixIcons.Play),
    BottomNavItem(Screen.Settings, "设置", MiuixIcons.Settings, MiuixIcons.Settings),
)
