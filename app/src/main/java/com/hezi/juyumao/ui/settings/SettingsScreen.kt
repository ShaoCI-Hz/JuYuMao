package com.hezi.juyumao.ui.settings

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.ui.theme.ThemeMode
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onNavigateToSmb: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToCache: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    val appViewModel: com.hezi.juyumao.ui.AppViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    val themeLabel = when (themeMode) {
        ThemeMode.DARK -> "深色"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.SYSTEM -> "跟随系统"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = "设置")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
        // Theme section
        item {
            SettingsSection(title = "外观") {
                SettingsItem(
                    icon = MiuixIcons.Theme,
                    title = "主题模式",
                    subtitle = themeLabel,
                    onClick = { showThemeDialog = true },
                )
            }
        }

        // SMB section
        item {
            SettingsSection(title = "NAS 连接") {
                SettingsItem(
                    icon = MiuixIcons.CloudFill,
                    title = "SMB 服务器管理",
                    subtitle = "管理 NAS 连接",
                    onClick = onNavigateToSmb,
                )
                // 自动连接开关（原为空 onClick 死按钮：设置从未接线，用户无法关闭启动自动重连）
                val smbAutoConnect by viewModel.smbAutoConnect.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                            onClick = { viewModel.setSmbAutoConnect(!smbAutoConnect) },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(MiuixIcons.Refresh, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动连接", style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface)
                        Text("WiFi 下自动连接已保存的 NAS", style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Switch(checked = smbAutoConnect, onCheckedChange = { viewModel.setSmbAutoConnect(it) })
                }
            }
        }

        // Cache section
        item {
            val cacheThreads by viewModel.cacheThreads.collectAsStateWithLifecycle()
            SettingsSection(title = "存储") {
                SettingsItem(
                    icon = MiuixIcons.Folder,
                    title = "缓存管理",
                    subtitle = "管理 NAS 下载、封面、歌词缓存",
                    onClick = onNavigateToCache,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Speed, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) // miuix-icons 无对应
                    Spacer(Modifier.width(16.dp))
                    Text("缓存线程数", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    var sliderValue by remember { mutableFloatStateOf(cacheThreads.toFloat()) }
                    LaunchedEffect(cacheThreads) { sliderValue = cacheThreads.toFloat() }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.setCacheThreads(sliderValue.toInt()) },
                        valueRange = 1f..8f,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.sliderColors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            foregroundColor = MiuixTheme.colorScheme.primary,
                        ),
                    )
                    Text("${cacheThreads}", style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        // Audio section
        item {
            val audioBufferSize by viewModel.audioBufferSize.collectAsStateWithLifecycle()
            val gaplessPlayback by viewModel.gaplessPlayback.collectAsStateWithLifecycle()
            val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
            val spectrumVisualizer by viewModel.spectrumVisualizer.collectAsStateWithLifecycle()
            SettingsSection(title = "音频") {
                SettingsItem(
                    icon = MiuixIcons.Tune,
                    title = "均衡器",
                    subtitle = "调节音频效果",
                    onClick = onNavigateToEqualizer,
                )
                // 无缝播放开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                            onClick = { viewModel.setGaplessPlayback(!gaplessPlayback) },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.HighQuality, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) // miuix-icons 无对应
                    Column(modifier = Modifier.weight(1f)) {
                        Text("无缝播放", style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface)
                        Text("同格式连续曲目切换无间隙", style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Switch(checked = gaplessPlayback, onCheckedChange = { viewModel.setGaplessPlayback(it) })
                }
                // 交叉淡化滑块
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MiuixIcons.Tune, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("交叉淡化", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    var sliderValue by remember { mutableFloatStateOf(crossfadeDuration.toFloat()) }
                    LaunchedEffect(crossfadeDuration) { sliderValue = crossfadeDuration.toFloat() }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.setCrossfadeDuration(sliderValue.toInt()) },
                        valueRange = 0f..2000f,
                        steps = 7,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.sliderColors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            foregroundColor = MiuixTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        if (crossfadeDuration == 0) "关" else "${crossfadeDuration}ms",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // 频谱可视化开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                            onClick = { viewModel.setSpectrumVisualizer(!spectrumVisualizer) },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(MiuixIcons.Tune, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("频谱可视化", style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface)
                        Text("播放页实时频谱（低端机可关闭）", style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Switch(checked = spectrumVisualizer, onCheckedChange = { viewModel.setSpectrumVisualizer(it) })
                }
                // 音频缓冲大小（滑块可调，HiRes 歌曲自动使用更大缓冲）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Memory, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) // miuix-icons 无对应
                    Spacer(Modifier.width(16.dp))
                    Text("音频缓冲大小", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    var sliderValue by remember { mutableFloatStateOf(audioBufferSize.toFloat()) }
                    LaunchedEffect(audioBufferSize) { sliderValue = audioBufferSize.toFloat() }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.setAudioBufferSize(sliderValue.toInt()) },
                        valueRange = 128f..1024f,
                        steps = 6,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.sliderColors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            foregroundColor = MiuixTheme.colorScheme.primary,
                        ),
                    )
                    Text("${audioBufferSize}KB", style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    text = "Hi-Res 歌曲自动使用 2 倍预缓冲，缓解 NAS 大文件串流卡顿",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // 当前输出设备与格式
                AudioOutputInfo()
            }
        }

        // Lyrics section
        item {
            val lyricsFontSize by viewModel.lyricsFontSize.collectAsStateWithLifecycle()
            val lyricsFontBold by viewModel.lyricsFontBold.collectAsStateWithLifecycle()
            SettingsSection(title = "歌词") {
                SettingsItem(
                    icon = MiuixIcons.NotesFill,
                    title = "歌词字体大小",
                    subtitle = "${lyricsFontSize.toInt()} sp",
                    onClick = { },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("14sp", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    // HIGH: 拖拽时用本地状态，松手后才写 DataStore
                    var sliderValue by remember { mutableFloatStateOf(lyricsFontSize) }
                    LaunchedEffect(lyricsFontSize) { sliderValue = lyricsFontSize }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.setLyricsFontSize(sliderValue) },
                        valueRange = 14f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.sliderColors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            foregroundColor = MiuixTheme.colorScheme.primary,
                        ),
                    )
                    Text("28sp", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                            onClick = { viewModel.setLyricsFontBold(!lyricsFontBold) },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.FormatBold, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) // miuix-icons 无对应
                    Text("歌词加粗", style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = lyricsFontBold, onCheckedChange = { viewModel.setLyricsFontBold(it) })
                }
            }
        }

        // About section
        item {
            SettingsSection(title = "关于") {
                SettingsItem(
                    icon = MiuixIcons.Info,
                    title = "版本",
                    subtitle = com.hezi.juyumao.BuildConfig.VERSION_NAME,
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Default.Code, // miuix-icons 无对应
                    title = "开源许可",
                    subtitle = "",
                    onClick = { },
                )
            }
        }

        // 引导
        item {
            SettingsSection(title = "引导") {
                SettingsItem(
                    icon = MiuixIcons.Info,
                    title = "重新查看引导",
                    subtitle = "再次展示首次使用引导",
                    onClick = { appViewModel.resetOnboarding() },
                )
            }
        }
    }
    }

    // Theme selector dialog
    if (showThemeDialog) {
        OverlayDialog(
            show = showThemeDialog,
            onDismissRequest = { showThemeDialog = false },
            title = "主题模式",
            content = {
                Column {
                    listOf(
                        ThemeMode.DARK to "深色",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.SYSTEM to "跟随系统",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                                    onClick = {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Text(text = label, style = MiuixTheme.textStyles.body1)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(text = "取消", onClick = { showThemeDialog = false })
                    }
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Card(
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceVariant,
            ),
            cornerRadius = 12.dp,
        ) {
            Column { content() }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(sinkAmount = 0.85f, animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f)),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 当前音频输出设备与格式展示（经 AudioManager/AudioTrack 查询） */
@Composable
private fun AudioOutputInfo() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var deviceName by remember { mutableStateOf("查询中...") }
    var formatInfo by remember { mutableStateOf("查询中...") }

    LaunchedEffect(Unit) {
        runCatching {
            val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            // 输出设备
            val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            val active = devices.firstOrNull { it.isSink && it.type != android.media.AudioDeviceInfo.TYPE_TELEPHONY }
                ?: devices.firstOrNull()
            val deviceLabel = when (active?.type) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "扬声器"
                android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙耳机"
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 音频设备"
                else -> active?.productName?.toString() ?: "未知"
            }
            // 输出采样率/格式（默认 44100Hz 16bit，蓝牙/HD 设备可能有差异）
            val sampleRate = am.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 44100
            val frameCount = am.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
            val fmt = if (frameCount > 0) "${sampleRate}Hz · ${frameCount}frames" else "${sampleRate}Hz"
            deviceName to fmt
        }.onSuccess {
            deviceName = it.first
            formatInfo = it.second
        }.onFailure {
            deviceName = "无法获取"
            formatInfo = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(MiuixIcons.Tune, null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "当前输出",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = if (formatInfo.isEmpty()) deviceName else "$deviceName · $formatInfo",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
