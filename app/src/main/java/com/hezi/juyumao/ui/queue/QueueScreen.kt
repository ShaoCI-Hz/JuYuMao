package com.hezi.juyumao.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回",
                )
            }
            Text(
                text = "播放队列 (${queue.size})",
                style = MiuixTheme.textStyles.title3,
            )
            IconButton(onClick = { viewModel.clearQueue() }) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "清空",
                )
            }
        }

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
                items(items = queue, key = { it.id }) { song ->
                    val isCurrent = queue.indexOf(song) == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playAt(queue.indexOf(song)) }
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
                        if (isCurrent) {
                            Text("播放中", style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
