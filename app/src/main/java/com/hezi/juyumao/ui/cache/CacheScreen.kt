package com.hezi.juyumao.ui.cache

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hezi.juyumao.data.local.cache.CacheManager
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CacheScreen(
    onBack: () -> Unit,
    viewModel: CacheViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "缓存管理",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
            },
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // 总览卡片
            item {
                Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
                    cornerRadius = 14.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("缓存占用", style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onPrimaryContainer)
                        Text(CacheManager.formatSize(uiState.totalSize),
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.onPrimaryContainer)
                        Text("封面 ${CacheManager.formatSize(uiState.albumArtSize)} · NAS下载 ${CacheManager.formatSize(uiState.nasDownloadSize)} · 歌词 ${CacheManager.formatSize(uiState.lyricsSize)} · 临时 ${CacheManager.formatSize(uiState.tempSize)}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }

            // 清理按钮
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.refreshSizes() }, modifier = Modifier.weight(1f),
                        cornerRadius = 8.dp,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceVariant,
                            contentColor = MiuixTheme.colorScheme.primary,
                        )) {
                        Icon(MiuixIcons.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新")
                    }
                    Button(onClick = { showClearDialog = true }, modifier = Modifier.weight(1f),
                        cornerRadius = 8.dp) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp)) // miuix-icons 无对应，语义近似
                        Spacer(Modifier.width(4.dp))
                        Text("清除缓存")
                    }
                }
            }

            // 操作反馈
            uiState.lastAction?.let { action ->
                item {
                    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                        cornerRadius = 10.dp) {
                        Text(action, modifier = Modifier.padding(12.dp),
                            style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }

            // 已下载的 NAS 歌曲
            item {
                Text("已下载的 NAS 歌曲 (${uiState.cachedNasSongs.size})",
                    style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.primary)
            }

            if (uiState.cachedNasSongs.isEmpty()) {
                item {
                    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        cornerRadius = 12.dp) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(MiuixIcons.Download, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text("暂无下载的歌曲。在浏览页长按 NAS 歌曲可下载到本地缓存，离线播放。",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            } else {
                items(uiState.cachedNasSongs) { cachedSong ->
                    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                        cornerRadius = 12.dp) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.AudioFile, null, tint = MiuixTheme.colorScheme.primary) // miuix-icons 无对应，语义近似
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cachedSong.fileName, style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(CacheManager.formatSize(cachedSong.file.length()),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            IconButton(onClick = { viewModel.deleteNasSong(cachedSong.songId) }) {
                                Icon(MiuixIcons.Delete, "删除", tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showClearDialog) {
        OverlayDialog(
            show = showClearDialog,
            onDismissRequest = { showClearDialog = false },
            title = "清除缓存",
            summary = "将清除所有缓存（封面、下载歌曲、歌词、临时文件），释放 ${CacheManager.formatSize(uiState.totalSize)} 空间。已下载的 NAS 歌曲删除后需重新下载。",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "全部清除",
                        onClick = {
                            viewModel.clearAllCache()
                            showClearDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    )
                    TextButton(text = "取消", onClick = { showClearDialog = false })
                }
            },
        )
    }
}
