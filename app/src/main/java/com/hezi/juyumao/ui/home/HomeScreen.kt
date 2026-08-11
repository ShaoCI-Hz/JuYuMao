package com.hezi.juyumao.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import com.hezi.juyumao.data.remote.smb.SmbConnectionState
import com.hezi.juyumao.ui.theme.FormatUtils
import java.io.File

@Composable
fun HomeScreen(
    onNavigateToPlayer: (Long) -> Unit = {},
    onNavigateToSmb: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToQueue: () -> Unit = {},
    onNavigateToPlaylist: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onPlayAll: (List<SongEntity>) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSleepTimer by remember { mutableStateOf(false) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.scanLocalMusic() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Header + 统计卡片
        item {
            Spacer(modifier = Modifier.height(48.dp))
            Text("局域猫播放器", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Icons.Default.MusicNote, "${uiState.songCount}", "歌曲", Modifier.weight(1f))
                StatCard(Icons.Default.Album, "${uiState.albumCount}", "专辑", Modifier.weight(1f))
                StatCard(Icons.Default.Person, "${uiState.artistCount}", "艺术家", Modifier.weight(1f))
                StatCard(Icons.Default.PlayCircle, "${uiState.playCount}", "播放", Modifier.weight(1f))
            }
        }

        // 本地扫描
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
                cornerRadius = 14.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("本地音乐", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (uiState.isScanning) uiState.scanMessage
                                   else if (uiState.songCount > 0) "已收录 ${uiState.songCount} 首"
                                   else "点击扫描设备音乐",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.isScanning) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        FilledTonalButton(onClick = { permissionLauncher.launch(audioPermission) }) { Text("扫描") }
                    }
                }
            }
        }

        // NAS 连接
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
                cornerRadius = 14.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NAS 连接", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (uiState.nasConnected) "已连接" else "未连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.nasConnected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = onNavigateToSmb) {
                        Text(if (uiState.nasConnected) "管理" else "连接")
                    }
                }
            }
        }

        // 快捷操作
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("快捷操作", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(Icons.Default.Equalizer, "均衡器", onNavigateToEqualizer, Modifier.weight(1f))
                QuickActionCard(Icons.Default.Timer, "定时关闭", { showSleepTimer = true }, Modifier.weight(1f))
                QuickActionCard(Icons.AutoMirrored.Filled.QueueMusic, "播放队列", onNavigateToQueue, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(Icons.Default.QueueMusic, "歌单", onNavigateToPlaylist, Modifier.weight(1f))
                QuickActionCard(Icons.Default.BarChart, "听歌报告", onNavigateToStatistics, Modifier.weight(1f))
                QuickActionCard(Icons.Default.Storage, "缓存管理", { }, Modifier.weight(1f))
            }
        }

        // 每日问候卡片（BUG-3/4 + FEAT-1 修复）
        item {
            Spacer(modifier = Modifier.height(24.dp))
            val dailyCard = uiState.dailyCard
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("${dailyCard.greeting}，${dailyCard.dateText}",
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        dailyCard.weatherText?.let { weather ->
                            Text(weather, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(dailyCard.quote, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("—— ${dailyCard.quoteAuthor}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End))
                }
            }
        }

        // 最近播放（BUG-2 修复）
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("最近播放", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(12.dp))
            if (uiState.recentlyPlayed.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("暂无播放记录", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyRow(modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items = uiState.recentlyPlayed, key = { it.id }) { song ->
                        RecentSongCard(song = song, onClick = { onNavigateToPlayer(song.id) })
                    }
                }
            }
        }
    }

    if (showSleepTimer) {
        com.hezi.juyumao.ui.sleep.SleepTimerSheet(
            onDismiss = { showSleepTimer = false },
        )
    }
}

@Composable
private fun RecentSongCard(song: SongEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.width(120.dp).clickable(onClick = onClick),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
        cornerRadius = 12.dp) {
        Column(modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!song.albumArtUri.isNullOrEmpty()) {
                AsyncImage(model = File(song.albumArtUri), contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null,
                        tint = MaterialTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(32.dp))
                }
            }
            Text(song.title, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(song.artist, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
        cornerRadius = 12.dp) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickActionCard(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surfaceVariant),
        cornerRadius = 12.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
