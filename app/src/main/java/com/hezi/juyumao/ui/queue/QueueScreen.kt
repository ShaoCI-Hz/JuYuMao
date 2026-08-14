package com.hezi.juyumao.ui.queue

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.domain.model.Song

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "播放队列 (${queue.size})",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                    )
                }
            },
            actions = {
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = "存为歌单",
                    )
                }
                IconButton(onClick = { viewModel.clearQueue() }) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "清空",
                    )
                }
            },
        )

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "队列为空",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items = queue, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = SinkFeedback(
                                    sinkAmount = 0.85f,
                                    animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                ),
                            ) { viewModel.playAt(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Music,
                            contentDescription = null,
                            tint = if (isCurrent) MiuixTheme.colorScheme.primary
                                   else MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MiuixTheme.textStyles.body1,
                                color = if (isCurrent) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${song.artist} · ${song.album}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 右侧：播放中标识 + 上移/下移
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (isCurrent) {
                                Text("播放中", style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary)
                            }
                            Row {
                                MoveButton(icon = MiuixIcons.ExpandLess, enabled = index > 0) { viewModel.move(index, index - 1) }
                                MoveButton(icon = MiuixIcons.ExpandMore, enabled = index < queue.size - 1) { viewModel.move(index, index + 1) }
                            }
                        }
                    }
                }
            }
        }
    }

    // 存为歌单对话框
    if (showSaveDialog) {
        OverlayDialog(
            show = true,
            title = "保存队列为歌单",
            onDismissRequest = { showSaveDialog = false },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = "歌单名称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = { showSaveDialog = false })
                    Button(
                        onClick = {
                            viewModel.saveAsPlaylist(playlistName)
                            playlistName = ""
                            showSaveDialog = false
                        },
                    ) { Text("保存") }
                }
            }
        }
    }

    // 保存结果提示（一次性）
    saveMessage?.let { msg ->
        OverlayDialog(
            show = true,
            title = "提示",
            onDismissRequest = { viewModel.consumeSaveMessage() },
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Text(msg, style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "确定", onClick = { viewModel.consumeSaveMessage() })
                }
            }
        }
    }
}

/** 队列行内上移/下移小按钮 */
@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceSecondary
                   else MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}
