package com.banlimusic.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackRow

@Composable
fun LibraryScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "音乐库",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("收藏") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("最近播放") },
            )
        }

        if (selectedTab == 0) {
            if (favorites.isEmpty()) {
                EmptyState(
                    title = "还没有收藏歌曲",
                    description = "在搜索结果或播放页点击心形图标收藏。",
                    icon = Icons.Rounded.FavoriteBorder,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "共 ${favorites.size} 首",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onPlayQueue(favorites, 0) }) {
                                Text("播放全部")
                            }
                        }
                    }
                    itemsIndexed(favorites, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { onPlayQueue(favorites, index) },
                            isFavorite = true,
                            onToggleFavorite = { viewModel.removeFavorite(track) },
                        )
                    }
                }
            }
        } else {
            if (history.isEmpty()) {
                EmptyState(
                    title = "还没有播放记录",
                    description = "播放过的歌曲会自动记录在这里。",
                    icon = Icons.Rounded.History,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "最近播放 ${history.size} 首",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::clearHistory) {
                                Text("清空记录")
                            }
                        }
                    }
                    itemsIndexed(history, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { onPlayQueue(history, index) },
                        )
                    }
                }
            }
        }
    }
}
