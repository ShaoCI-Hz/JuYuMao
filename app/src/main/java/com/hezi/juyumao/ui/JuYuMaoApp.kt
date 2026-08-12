package com.hezi.juyumao.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hezi.juyumao.ui.components.MiniPlayerBar
import com.hezi.juyumao.ui.components.PremiumBottomNavBar
import com.hezi.juyumao.ui.navigation.JuYuMaoNavGraph
import com.hezi.juyumao.ui.navigation.Screen
import com.hezi.juyumao.ui.navigation.bottomNavItems
import com.hezi.juyumao.ui.theme.JuYuMaoTheme
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

@Composable
fun JuYuMaoApp() {
    val appViewModel: AppViewModel = hiltViewModel()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val onboardingCompleted by appViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    // 「连接 NAS」引导：完成后直接进入 SMB 连接页
    var startAtSmb by remember { mutableStateOf(false) }

    JuYuMaoTheme(themeMode = themeMode) {
        // Miuix OverlayDialog/OverlayBottomSheet/OverlayListPopup 的 DialogEntry/PopupEntry 内部
        // 使用 NavigationBackHandler（androidx.navigationevent 预测性返回），要求读取
        // LocalNavigationEventDispatcherOwner。该 Local 通常由 NavHost 提供，但弹窗内容组合在
        // Scaffold 的 popup 层（位于 NavHost 之外），读不到 → IllegalStateException 闪退。
        // 在根提供 owner（rememberNavigationEventDispatcherOwner 内部包 Compose 实现），
        // 修复"点击主题设置/缓存清除/歌单删除/倍速菜单/睡眠倒计时"等所有 Overlay 组件闪退。
        // parent 必须显式传 null：不传时默认读取 LocalNavigationEventDispatcherOwner.current，
        // 而根处没有父 owner，会抛 "No NavigationEventDispatcherOwner provided"。
        val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner
        ) {
            if (!onboardingCompleted) {
                com.hezi.juyumao.ui.onboarding.OnboardingScreen(
                    onStart = {
                        appViewModel.completeOnboarding()
                    },
                    onConnectNas = {
                        startAtSmb = true
                        appViewModel.completeOnboarding()
                    },
                )
            } else {
                JuYuMaoAppContent(appViewModel, startAtSmb)
            }
        }
    }
}

@Composable
private fun JuYuMaoAppContent(
    appViewModel: AppViewModel,
    startAtSmb: Boolean = false,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 引导「连接 NAS」后直达 SMB 页（带引导提示）
    LaunchedEffect(startAtSmb) {
        if (startAtSmb) {
            navController.navigate(Screen.SmbConnect.createRoute(guide = true)) { popUpTo(Screen.Home.route) }
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Browse.route,
        Screen.Search.route,
        Screen.Settings.route,
    )

    val currentSong by appViewModel.currentSong.collectAsStateWithLifecycle()
    val artworkUri by appViewModel.artworkUri.collectAsStateWithLifecycle()
    val isPlaying by appViewModel.isPlaying.collectAsStateWithLifecycle()
    val position by appViewModel.position.collectAsStateWithLifecycle()
    val duration by appViewModel.duration.collectAsStateWithLifecycle()
    val reconnectState by appViewModel.reconnectState.collectAsStateWithLifecycle()
    val playbackError by appViewModel.playbackError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 自动重连完成提示
    LaunchedEffect(reconnectState.message) {
        reconnectState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            appViewModel.clearReconnectMessage()
        }
    }

    // 播放错误提示（解码失败等）
    LaunchedEffect(playbackError) {
        playbackError?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            appViewModel.clearPlaybackError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                    initialOffsetY = { it }
                ) + fadeIn(),
                exit = slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                    targetOffsetY = { it }
                ) + fadeOut(),
            ) {
                androidx.compose.foundation.layout.Column {
                    MiniPlayerBar(
                        onPlayerClick = {
                            val songId = currentSong?.id ?: return@MiniPlayerBar
                            navController.navigate(Screen.Player.createRoute(songId))
                        },
                        songTitle = currentSong?.title,
                        songArtist = currentSong?.artist,
                        artworkUri = artworkUri,
                        isPlaying = isPlaying,
                        progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onPlayPauseClick = { appViewModel.togglePlay() },
                    )
                    PremiumBottomNavBar(
                        items = bottomNavItems,
                        currentRoute = currentRoute ?: Screen.Home.route,
                        onItemSelected = { item ->
                            if (item.screen.route != currentRoute) {
                                navController.navigate(item.screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        val isPlayerPage = currentRoute?.startsWith("player/") == true
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isPlayerPage) Modifier.padding(paddingValues) else Modifier),
        ) {
            JuYuMaoNavGraph(navController = navController)
        }
    }
}
