package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopLibraryScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val playback by state.playback.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    val source = if (filter == 0) favorites else history
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }

    val visible = remember(source, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            source
        } else {
            source.filter { track ->
                track.title.contains(keyword, ignoreCase = true) ||
                    track.artistLine.contains(keyword, ignoreCase = true) ||
                    track.album?.title?.contains(keyword, ignoreCase = true) == true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp)) {
        Text(
            text = "音乐库",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "收藏 ${favorites.size} 首，最近播放 ${history.size} 首",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == 0,
                onClick = { filter = 0 },
                label = { Text("我的收藏") },
            )
            FilterChip(
                selected = filter == 1,
                onClick = { filter = 1 },
                label = { Text("最近播放") },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(0.62f),
                singleLine = true,
                placeholder = { Text(if (filter == 0) "在收藏中筛选" else "在播放记录中筛选") },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (query.isBlank()) {
                    "数据保存在本机 JSON 文件中"
                } else {
                    "匹配 ${visible.size} 首"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))

        if (visible.isEmpty()) {
            DesktopEmptyState(
                title = when {
                    source.isEmpty() && filter == 0 -> "还没有收藏歌曲"
                    source.isEmpty() -> "还没有播放记录"
                    else -> "没有匹配的歌曲"
                },
                description = when {
                    source.isEmpty() && filter == 0 ->
                        "在搜索结果中点击心形图标即可加入桌面音乐库。"
                    source.isEmpty() -> "播放歌曲后会自动记录在这里。"
                    else -> "试试更短的关键词。"
                },
                glyph = "♫",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(visible, key = { it.id }) { track ->
                    DesktopTrackRow(
                        track = track,
                        loader = state.container.artworkLoader,
                        allowRemoteArtwork = settings.requestListArtwork,
                        favorite = track.id in favoriteIds,
                        current = playback.track?.id == track.id,
                        onPlay = { state.play(track, visible) },
                        onToggleFavorite = { state.toggleFavorite(track) },
                        onDownload = { state.download(track) },
                    )
                }
            }
        }
    }
}
