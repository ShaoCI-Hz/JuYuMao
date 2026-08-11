package com.hezi.juyumao.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hezi.juyumao.data.local.db.entity.SongEntity
import java.io.File

/**
 * 歌单页：列表 + 详情 一体（currentPlaylist 为空显示列表，否则显示详情）
 */
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onPlayAll: (List<SongEntity>) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentPlaylist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val currentSongs by viewModel.currentSongs.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<com.hezi.juyumao.data.local.db.entity.PlaylistEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<com.hezi.juyumao.data.local.db.entity.PlaylistEntity?>(null) }

    if (currentPlaylist != null) {
        // ── 歌单详情 ──
        PlaylistDetail(
            playlist = currentPlaylist!!,
            songs = currentSongs,
            onBack = { viewModel.closePlaylist() },
            onPlayAll = { onPlayAll(currentSongs) },
            onRemoveSong = { viewModel.removeSong(currentPlaylist!!.id, it) },
            onRename = { viewModel.renamePlaylist(currentPlaylist!!.id, it) },
        )
    } else {
        // ── 歌单列表 ──
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                Text(
                    text = "歌单",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, "新建歌单")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.QueueMusic, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        Text("还没有歌单，点击右上角 + 新建", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp)) {
                    items(playlists, key = { it.playlist.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openPlaylist(item.playlist.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.playlist.name, style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${item.songCount} 首", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { renameTarget = item.playlist }) {
                                Icon(Icons.Default.Edit, "重命名", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { deleteTarget = item.playlist }) {
                                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 新建歌单对话框 ──
    if (showCreateDialog) {
        NameDialog(
            title = "新建歌单",
            initialName = "",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    // ── 重命名对话框 ──
    renameTarget?.let { target ->
        NameDialog(
            title = "重命名歌单",
            initialName = target.name,
            onConfirm = { name ->
                viewModel.renamePlaylist(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    // ── 删除确认 ──
    deleteTarget?.let { target ->
        OverlayDialog(
            show = true,
            title = "删除歌单",
            summary = "确定删除歌单「${target.name}」吗？歌单内的歌曲不会被删除。",
            onDismissRequest = { deleteTarget = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = "取消",
                    onClick = { deleteTarget = null },
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(textColor = MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

/** 歌单详情页 */
@Composable
private fun PlaylistDetail(
    playlist: com.hezi.juyumao.data.local.db.entity.PlaylistEntity,
    songs: List<SongEntity>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onRemoveSong: (Long) -> Unit,
    onRename: (String) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("${songs.size} 首歌曲", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Default.Edit, "重命名")
            }
        }

        // 整单播放按钮
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalButton(
                onClick = onPlayAll,
                enabled = songs.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("播放全部")
            }
        }

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单为空", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)) {
                items(songs, key = { it.id }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!song.albumArtUri.isNullOrEmpty()) {
                            AsyncImage(model = File(song.albumArtUri), contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemoveSong(song.id) }) {
                            Icon(Icons.Default.RemoveCircleOutline, "移除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        NameDialog(
            title = "重命名歌单",
            initialName = playlist.name,
            onConfirm = { name -> onRename(name); showRename = false },
            onDismiss = { showRename = false },
        )
    }
}

/** 文本输入对话框（新建/重命名共用） */
@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "歌单名称",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
            )
            TextButton(
                text = "确定",
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            )
        }
    }
}

/**
 * 添加歌曲到歌单弹层（从歌曲详情/搜索等入口调用）
 */
@Composable
fun AddToPlaylistDialog(
    song: SongEntity,
    onDismiss: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    OverlayDialog(
        show = true,
        title = "添加到歌单",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (playlists.isEmpty()) {
                Text("还没有歌单", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                playlists.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addSongToPlaylist(item.playlist.id, song.id)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(item.playlist.name, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("(${item.songCount})", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(
                text = "新建歌单",
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = "关闭",
                onClick = onDismiss,
            )
        }
    }

    if (showCreate) {
        NameDialog(
            title = "新建歌单",
            initialName = "",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}
