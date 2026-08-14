package com.hezi.juyumao.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hezi.juyumao.ui.cache.CacheScreen
import com.hezi.juyumao.ui.equalizer.EqualizerScreen
import com.hezi.juyumao.ui.home.HomeScreen
import com.hezi.juyumao.ui.player.PlayerScreen
import com.hezi.juyumao.ui.queue.QueueScreen
import com.hezi.juyumao.ui.search.SearchScreen
import com.hezi.juyumao.ui.settings.SettingsScreen
import com.hezi.juyumao.ui.smb.SmbConnectScreen

@Composable
fun JuYuMaoNavGraph(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            fadeIn(animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                initialOffsetY = { it / 20 }
            )
        },
        exitTransition = {
            fadeOut(animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f))
        },
        popEnterTransition = {
            fadeIn(animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f))
        },
        popExitTransition = {
            fadeOut(animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f)) +
            slideOutVertically(
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                targetOffsetY = { it / 20 }
            )
        },
    ) {
        composable(Screen.Home.route) {
            val appViewModel: com.hezi.juyumao.ui.AppViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            HomeScreen(
                onNavigateToPlayer = { songId -> navController.navigate(Screen.Player.createRoute(songId)) },
                onNavigateToSmb = { navController.navigate(Screen.SmbConnect.route) },
                onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                onNavigateToQueue = { navController.navigate(Screen.Queue.route) },
                onNavigateToPlaylist = { navController.navigate(Screen.Playlist.route) },
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) },
                onNavigateToCache = { navController.navigate(Screen.Cache.route) },
                onPlayAll = { songs -> appViewModel.playSongs(songs) },
            )
        }
        composable(Screen.Browse.route) {
            com.hezi.juyumao.ui.browse.BrowseScreen(
                onSongClick = { songId ->
                    navController.navigate(Screen.Player.createRoute(songId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onSongClick = { songId -> navController.navigate(Screen.Player.createRoute(songId)) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToSmb = { navController.navigate(Screen.SmbConnect.route) },
                onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                onNavigateToCache = { navController.navigate(Screen.Cache.route) },
            )
        }
        // 播放器页面：接收 songId 参数
        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("songId") { type = NavType.LongType }),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) },
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onOpenQueue = { navController.navigate(Screen.Queue.route) },
            )
        }
        composable(Screen.Queue.route) {
            QueueScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.SmbConnect.route,
            arguments = listOf(
                androidx.navigation.navArgument("guide") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val guide = entry.arguments?.getBoolean("guide") ?: false
            SmbConnectScreen(
                onBack = { navController.popBackStack() },
                showGuideTip = guide,
            )
        }
        composable(Screen.Equalizer.route) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Cache.route) {
            CacheScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Playlist.route) {
            val appViewModel: com.hezi.juyumao.ui.AppViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            com.hezi.juyumao.ui.playlist.PlaylistScreen(
                onBack = { navController.popBackStack() },
                onPlayAll = { songs -> appViewModel.playSongs(songs) },
                onOpenSmartPlaylist = { navController.navigate(Screen.SmartPlaylist.route) },
            )
        }
        composable(Screen.SmartPlaylist.route) {
            com.hezi.juyumao.ui.smartplaylist.SmartPlaylistScreen(
                onBack = { navController.popBackStack() },
                onSongClick = { songId -> navController.navigate(Screen.Player.createRoute(songId)) },
            )
        }
        composable(Screen.Statistics.route) {
            com.hezi.juyumao.ui.statistics.StatisticsScreen(
                onBack = { navController.popBackStack() },
                onSongClick = { songId -> navController.navigate(Screen.Player.createRoute(songId)) },
            )
        }
    }
}
