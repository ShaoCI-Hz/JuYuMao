package com.hezi.juyumao.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hezi.juyumao.ui.player.components.*

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val artworkUri by viewModel.artworkUri.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val lyricsFontSize by viewModel.lyricsFontSize.collectAsStateWithLifecycle()
    val lyricsFontBold by viewModel.lyricsFontBold.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val spectrumEnabled by viewModel.spectrumEnabled.collectAsStateWithLifecycle()
    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()

    var isImmersive by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(0) }
    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // 封面入场动画：列表 → 播放器「放大入场」（T11.1 备选方案）
    var entered by remember { mutableStateOf(false) }
    val enterScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0.85f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.65f,
            stiffness = 300f,
        ),
        label = "cover_enter_scale",
    )
    val enterAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(350),
        label = "cover_enter_alpha",
    )
    LaunchedEffect(Unit) { entered = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 背景层：封面模糊 + 主色调 + 流光 =====
        PlayerBackground(artworkUri = artworkUri)

        // ===== 内容层 =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶栏
            PlayerTopBar(
                onBack = onBack,
                title = if (isImmersive) "" else "正在播放",
                onImmersiveToggle = { isImmersive = !isImmersive },
                isImmersive = isImmersive,
            )

            // 封面/歌词上下滑动切换（带入场放大动画）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = enterScale
                        scaleY = enterScale
                        alpha = enterAlpha
                    },
            ) {
                CoverLyricsPager(
                    artworkUri = artworkUri,
                    lyricsData = lyrics,
                    currentPositionMs = position,
                    isPlaying = isPlaying,
                    showLyrics = showLyrics,
                    lyricsFontSize = lyricsFontSize,
                    lyricsFontBold = lyricsFontBold,
                    onLineClick = { viewModel.seekTo(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 频谱可视化（仅播放时显示）
            if (spectrumEnabled && isPlaying) {
                SpectrumBars(
                    bars = spectrum,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 歌曲信息（完整模式才显示）
            AnimatedVisibility(
                visible = !isImmersive,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        currentSong?.title ?: "未知歌曲",
                        style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val subtitle = buildString {
                        append(currentSong?.artist ?: "未知艺术家")
                        if (!currentSong?.album.isNullOrEmpty() && currentSong?.album != "未知专辑") {
                            append(" · ")
                            append(currentSong!!.album)
                        }
                    }
                    Text(
                        subtitle,
                        style = MiuixTheme.textStyles.body2,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 音频规格展示（HiRes 金色徽标 + 采样率/位深/码率）
                    if (currentSong != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AudioSpecRow(song = currentSong!!)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 进度条 + 倍速按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerSlider(
                    position = position,
                    duration = duration,
                    onSeek = { viewModel.seekTo(it) },
                    modifier = Modifier.weight(1f),
                )
                // 倍速菜单
                Box {
                    TextButton(
                        text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier,
                        textStyle = MiuixTheme.textStyles.footnote1,
                        colors = ButtonDefaults.textButtonColors(textColor = Color.White.copy(alpha = 0.8f)),
                    )
                    OverlayListPopup(
                        show = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                    ) {
                        ListPopupColumn {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                Text(
                                    text = if (speed == 1.0f) "正常" else "${speed}x",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playbackSpeed = speed
                                            viewModel.setPlaybackSpeed(speed)
                                            showSpeedMenu = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MiuixTheme.textStyles.body1,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 控制按钮
            if (isImmersive) {
                ImmersiveControlRow(
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.togglePlay() },
                    onPrevious = { viewModel.previous() },
                    onNext = { viewModel.next() },
                )
            } else {
                FullControlRow(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    onPrevious = { viewModel.previous() },
                    onPlayPause = { viewModel.togglePlay() },
                    onNext = { viewModel.next() },
                    onShuffle = {
                        shuffleEnabled = !shuffleEnabled
                        viewModel.setShuffle(shuffleEnabled)
                    },
                    onRepeat = {
                        repeatMode = (repeatMode + 1) % 3
                        viewModel.setRepeat(repeatMode)
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部功能栏（完整模式才显示）
            AnimatedVisibility(
                visible = !isImmersive,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200)),
            ) {
                BottomFunctionBar(
                    showLyrics = showLyrics,
                    isFavorite = isFavorite,
                    onLyricsClick = { showLyrics = !showLyrics },
                    onQueueClick = onOpenQueue,
                    onFavoriteClick = { viewModel.toggleFavorite() },
                    onMoreClick = { showAddToPlaylist = true },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 添加到歌单弹层（更多按钮）
    if (showAddToPlaylist && currentSong != null) {
        com.hezi.juyumao.ui.playlist.AddToPlaylistDialog(
            song = currentSong!!,
            onDismiss = { showAddToPlaylist = false },
        )
    }
}

/** 音频规格行：HiRes 徽标 + 「采样率/位深 · 格式」 */
@Composable
private fun AudioSpecRow(song: com.hezi.juyumao.data.local.db.entity.SongEntity) {
    val specs = buildList {
        if (song.sampleRate > 0) add("${song.sampleRate / 1000.0}kHz".replace(".0", ""))
        if (song.bitsPerSample > 0) add("${song.bitsPerSample}bit")
        if (song.bitrate > 0) add("${(song.bitrate / 1000).coerceAtLeast(1)}kbps")
        if (song.mimeType.isNotBlank()) {
            add(song.mimeType.substringAfter("/").uppercase())
        }
    }
    if (specs.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (song.isHiRes) {
            val isDsd = song.filePath.substringAfterLast('.', "").lowercase() in setOf("dsf", "dff")
            Box(
                modifier = Modifier
                    .background(
                        color = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (isDsd) "DSD" else "Hi-Res",
                    style = MiuixTheme.textStyles.footnote2,
                    color = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = specs.joinToString(" · "),
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}
