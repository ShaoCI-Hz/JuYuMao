package com.hezi.juyumao.ui.equalizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 选中态完全由 AudioEffectsManager 的 state.currentPreset 派生（本地副本与真实预设脱节
    // 会导致"自定义"chip 不通知管理器、退出重进后高亮回旧预设）
    val currentPreset = state.currentPreset.toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SmallTopAppBar(
            title = "均衡器",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 频谱可视化（复用 SpectrumAnalyzer 数据，数据在 SpectrumBars 内部订阅避免高频重组）
        com.hezi.juyumao.ui.player.components.SpectrumBars(
            spectrumFlow = viewModel.spectrum,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Enable toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceVariant,
            ),
            cornerRadius = 14.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "均衡器",
                    style = MiuixTheme.textStyles.body1,
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Presets
        if (state.presets.isNotEmpty()) {
            Text(
                text = "预设",
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.presets) { preset ->
                    MiuixFilterChip(
                        selected = currentPreset == preset.index.toInt(),
                        onClick = {
                            viewModel.usePreset(preset.index)
                        },
                        label = preset.name,
                    )
                }
                item {
                    MiuixFilterChip(
                        selected = currentPreset == -1,
                        onClick = { viewModel.usePreset(-1) },
                        label = "自定义",
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bands
        if (state.bands.isNotEmpty()) {
            Text(
                text = "频段调节",
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))

            state.bands.forEach { band ->
                // 拖动时用本地值，外部 state 变化时才同步（避免每帧重组）
                var sliderValue by remember(band.index) {
                    mutableFloatStateOf(band.currentLevel.toFloat())
                }
                // 外部值变化时更新本地（非拖动状态）
                LaunchedEffect(band.currentLevel) {
                    sliderValue = band.currentLevel.toFloat()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatFrequency(band.centerFreq),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = "${sliderValue.toInt()} dB",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            // 手动调频即自定义模式：由 AudioEffectsManager.setBandLevel 统一置 currentPreset=-1
                        },
                        onValueChangeFinished = {
                            viewModel.setBandLevel(band.index, sliderValue.toInt().toShort())
                        },
                        valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.sliderColors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            foregroundColor = MiuixTheme.colorScheme.primary,
                            backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 音效增强（T10.7）：低音/虚拟环绕/响度 ──
        Text(
            text = "音效增强",
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 低音增强
        if (state.bassBoostAvailable) {
            var bassValue by remember { mutableFloatStateOf(state.bassBoostStrength.toFloat()) }
            LaunchedEffect(state.bassBoostStrength) { bassValue = state.bassBoostStrength.toFloat() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "低音增强",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.bassBoostEnabled,
                        onCheckedChange = { viewModel.setBassBoostEnabled(it) },
                    )
                }
                Slider(
                    value = bassValue,
                    onValueChange = { bassValue = it },
                    onValueChangeFinished = { viewModel.setBassBoostStrength(bassValue.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.sliderColors(
                        thumbColor = MiuixTheme.colorScheme.primary,
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                )
            }
        }

        // 虚拟环绕
        if (state.virtualizerAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "虚拟环绕",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.virtualizerEnabled,
                    onCheckedChange = { viewModel.setVirtualizerEnabled(it) },
                )
            }
        }

        // 响度增强
        if (state.loudnessAvailable) {
            var loudnessValue by remember { mutableFloatStateOf(state.loudnessGain.toFloat()) }
            LaunchedEffect(state.loudnessGain) { loudnessValue = state.loudnessGain.toFloat() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "响度增强",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.loudnessGain != 0,
                        onCheckedChange = { enabled ->
                            viewModel.setLoudnessGain(if (enabled) 1000 else 0)
                        },
                    )
                }
                Slider(
                    value = loudnessValue,
                    onValueChange = { loudnessValue = it },
                    onValueChangeFinished = { viewModel.setLoudnessGain(loudnessValue.toInt()) },
                    valueRange = 0f..4000f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.sliderColors(
                        thumbColor = MiuixTheme.colorScheme.primary,
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                )
            }
        }

        // 设备不支持时提示
        if (!state.bassBoostAvailable && !state.virtualizerAvailable && !state.loudnessAvailable) {
            Text(
                text = "当前设备不支持音效增强",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatFrequency(milliHz: Int): String {
    val hz = milliHz / 1000
    return if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
}

/** Miuix 风格的筛选 Chip（替代 M3 FilterChip） */
@Composable
private fun MiuixFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(
                    sinkAmount = 0.85f,
                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                ),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}
