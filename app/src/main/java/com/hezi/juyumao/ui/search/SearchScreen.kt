package com.hezi.juyumao.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

@Composable
fun SearchScreen(
    onSongClick: (Long) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "搜索",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search bar
        TextField(
            value = query,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = "搜索歌曲、艺术家、专辑...",
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Search,
                    contentDescription = null,
                )
            },
            cornerRadius = 14.dp,
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (query.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "输入关键词搜索音乐",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未找到匹配的歌曲",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                items(items = results, key = { it.id }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!song.albumArtUri.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = java.io.File(song.albumArtUri),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Music,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
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
                        // 收藏按钮
                        IconButton(
                            onClick = { viewModel.toggleFavorite(song) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                // miuix-icons 无空心版 Favorites，未收藏态用半透明区分
                                imageVector = MiuixIcons.FavoritesFill,
                                contentDescription = if (song.isFavorite) "取消收藏" else "收藏",
                                tint = if (song.isFavorite) MiuixTheme.colorScheme.primary
                                       else MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
