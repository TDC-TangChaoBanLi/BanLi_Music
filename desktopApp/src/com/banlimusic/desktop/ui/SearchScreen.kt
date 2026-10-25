package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopSearchScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val playback by state.playback.collectAsState()
    val favoriteIds = state.favorites.collectAsState().value.mapTo(mutableSetOf()) { it.id }
    val searchHistory by state.searchHistory.collectAsState()
    val search = state.search

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp)) {
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "从 GD 音乐台启用的来源中并行搜索、合并去重。每页 ${settings.searchPageSize} 条。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search.query,
                onValueChange = state::updateQuery,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            state.submitSearch()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                placeholder = { Text("歌曲、歌手或专辑") },
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = state::submitSearch,
                enabled = search.query.isNotBlank() && !search.loading,
            ) {
                Text("搜索")
            }
        }
        Spacer(Modifier.height(14.dp))

        if (search.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }

        search.error?.let { message ->
            DesktopErrorBanner(message = message, onRetry = state::retrySearch)
            Spacer(Modifier.height(10.dp))
        }

        if (search.results.isEmpty() && !search.loading && searchHistory.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "搜索历史",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = state::clearSearchHistory) { Text("清空") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchHistory, key = { it }) { query ->
                    FilterChip(
                        selected = false,
                        onClick = { state.useSearchHistory(query) },
                        label = { Text(query) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (search.results.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "“${search.submittedQuery}” 共 ${search.results.size} 首",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "第 ${search.page} 页",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (search.results.isEmpty() && !search.loading && search.error == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                DesktopEmptyState(
                    title = if (search.submittedQuery.isBlank()) {
                        "输入关键词开始搜索"
                    } else {
                        "没有找到结果"
                    },
                    description = if (search.submittedQuery.isBlank()) {
                        "搜索需要手动触发，以减少 API 请求。"
                    } else {
                        "可以尝试更换关键词，或在设置中启用更多音乐源。"
                    },
                    glyph = "⌕",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(search.results, key = { it.id }) { track ->
                    DesktopTrackRow(
                        track = track,
                        loader = state.container.artworkLoader,
                        allowRemoteArtwork = settings.requestListArtwork,
                        favorite = track.id in favoriteIds,
                        current = playback.track?.id == track.id,
                        onPlay = { state.play(track, search.results) },
                        onToggleFavorite = { state.toggleFavorite(track) },
                        onDownload = { state.download(track) },
                    )
                }
                item {
                    when {
                        search.loadingMore -> Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(24.dp).height(24.dp),
                            )
                        }

                        search.hasMore -> Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = state::loadMore) {
                                Text("加载更多")
                            }
                        }

                        search.results.isNotEmpty() -> Text(
                            text = "已加载全部结果",
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
