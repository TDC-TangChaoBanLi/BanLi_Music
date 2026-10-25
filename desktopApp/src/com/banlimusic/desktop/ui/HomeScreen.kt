package com.banlimusic.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.model.Track
import com.banlimusic.desktop.data.DesktopDownloadStatus
import java.util.Calendar

@Composable
fun DesktopHomeScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()
    val folders by state.favoriteFolders.collectAsState()
    val downloads by state.downloads.collectAsState()
    val playback by state.playback.collectAsState()
    val downloadedIds = downloads
        .filter { it.status == DesktopDownloadStatus.COMPLETED }
        .mapTo(mutableSetOf()) { it.track.id }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "早上好，今天想听点什么？"
            in 12..17 -> "下午好，让音乐陪着你"
            in 18..22 -> "晚上好，放松一下吧"
            else -> "夜深了，听一首安静的歌"
        }
    }
    val sourceNames = SUPPORTED_SOURCES
        .filter { it.id in settings.enabledSources }
        .joinToString("、") { it.label }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "BanLiMusic",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = state::openSearch),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "搜索歌曲、歌手或专辑",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (sourceNames.isNotBlank()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前音源",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = sourceNames,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "最近播放",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = "播放记录会出现在这里，先搜索一首喜欢的歌吧。",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(history.take(10), key = { _, track -> track.id }) { index, track ->
                        RecentCard(
                            track = track,
                            loader = state.container.artworkLoader,
                            allowRemote = settings.requestListArtwork,
                            onClick = { state.play(track, history) },
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的收藏",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (favorites.isNotEmpty()) {
                    TextButton(onClick = { state.selectTab(DesktopTab.LIBRARY) }) {
                        Text("查看全部")
                    }
                }
            }
        }
        if (favorites.isEmpty()) {
            item {
                DesktopEmptyState(
                    title = "还没有收藏",
                    description = "在搜索结果或播放栏中点击心形图标即可收藏。",
                )
            }
        } else {
            itemsIndexed(favorites.take(5), key = { _, track -> track.id }) { _, track ->
                DesktopTrackRow(
                    track = track,
                    loader = state.container.artworkLoader,
                    allowRemoteArtwork = settings.requestListArtwork,
                    favorite = true,
                    current = playback.track?.id == track.id,
                    onPlay = { state.play(track, favorites) },
                    onToggleFavorite = { state.toggleFavorite(track) },
                    onDownload = { state.download(track) },
                    showFavorite = false,
                    downloaded = track.id in downloadedIds,
                    categoryLabel = folders[track.id],
                )
            }
        }

        item {
            Text(
                text = "音乐数据来自 GD 音乐台（music.gdstudio.xyz），仅供学习参考，请勿用于商业用途。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun RecentCard(
    track: Track,
    loader: DesktopArtworkLoader,
    allowRemote: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(148.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            DesktopTrackArtwork(
                track = track,
                loader = loader,
                allowRemote = allowRemote,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artistLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
