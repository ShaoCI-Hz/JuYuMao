package com.hezi.juyumao.ui.statistics

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import java.io.File

@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onSongClick: (Long) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "听歌报告",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
            },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(MiuixIcons.Refresh, "刷新")
                }
            },
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // 总览卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        cornerRadius = 14.dp,
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("累计播放", style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.primary)
                            Text("${uiState.totalPlayCount} 次", style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                                color = MiuixTheme.colorScheme.onBackground)
                            Text(
                                text = "累计时长 ${formatDuration(uiState.totalPlayDurationMs)} · 本周 ${uiState.weekPlayCount} 次 · 本月 ${uiState.monthPlayCount} 次",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }

                // TOP 歌曲
                if (uiState.topSongs.isNotEmpty()) {
                    item {
                        Text("TOP 10 歌曲", style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(uiState.topSongs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = SinkFeedback(
                                        sinkAmount = 0.85f,
                                        animationSpec = spring(dampingRatio = 0.99f, stiffness = 986.96f),
                                    ),
                                ) { onSongClick(song.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!song.albumArtUri.isNullOrEmpty()) {
                                AsyncImage(model = File(song.albumArtUri), contentDescription = null,
                                    modifier = Modifier.size(40.dp).squircleClip(cornerRadius = 6.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            } else {
                                Icon(MiuixIcons.Music, null,
                                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Text("${song.playCount} 次", style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }

                // TOP 艺术家
                if (uiState.topArtists.isNotEmpty()) {
                    item {
                        Text("TOP 10 艺术家", style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(uiState.topArtists, key = { it.first }) { (artist, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .squircleBackground(
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        cornerRadius = 8.dp,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(MiuixIcons.Contacts, null,
                                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Text(artist, style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count 次", style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }

                if (uiState.totalPlayCount == 0L) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center) {
                            Text("还没有播放记录，去听首歌吧",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val totalMinutes = ms / 60000
    if (totalMinutes < 60) return "$totalMinutes 分钟"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (mins > 0) "$hours 小时 $mins 分钟" else "$hours 小时"
}
