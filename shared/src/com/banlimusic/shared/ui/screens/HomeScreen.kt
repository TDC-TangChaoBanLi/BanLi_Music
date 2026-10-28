/**
 * 文件作用：两端共用的首页布局，展示快捷入口、最近播放与收藏。
 */
package com.banlimusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.model.Track
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiSearchEntry
import com.banlimusic.shared.ui.components.BanLiSectionHeader
import java.util.Calendar

data class HomeUiState(
    val recentTracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val enabledSourceIds: Set<String> = emptySet(),
)

@Composable
fun BanLiHomeScreen(
    state: HomeUiState,
    onSearchClick: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    artwork: @Composable (Track, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    searchIcon: ImageVector? = null,
    recentTrackLimit: Int = Int.MAX_VALUE,
) {
    val greeting = remember { homeGreeting() }
    val sourceNames = state.enabledSourceIds
        .let { enabled -> SUPPORTED_SOURCES.filter { it.id in enabled } }
        .joinToString("、") { it.label }
    val recent = state.recentTracks.take(recentTrackLimit)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
            BanLiSearchEntry(
                onClick = onSearchClick,
                searchIcon = searchIcon,
            )
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
            BanLiSectionHeader(title = "最近播放")
        }
        if (recent.isEmpty()) {
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
                    itemsIndexed(recent, key = { _, track -> track.id }) { index, track ->
                        RecentCard(
                            track = track,
                            artwork = artwork,
                            onClick = { onPlayQueue(recent, index) },
                        )
                    }
                }
            }
        }

        item {
            BanLiSectionHeader(
                title = "我的收藏",
                actionText = if (state.favoriteTracks.isNotEmpty()) "查看全部" else null,
                onAction = onOpenLibrary,
            )
        }
        if (state.favoriteTracks.isEmpty()) {
            item {
                BanLiEmptyState(
                    title = "还没有收藏",
                    description = "在播放页或搜索结果中点击心形图标即可收藏。",
                )
            }
        } else {
            itemsIndexed(
                items = state.favoriteTracks.take(5),
                key = { _, track -> track.id },
            ) { index, track ->
                com.banlimusic.shared.ui.components.BanLiTrackListRow(
                    track = track,
                    artwork = { artworkModifier -> artwork(track, artworkModifier) },
                    onClick = { onPlayQueue(state.favoriteTracks, index) },
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
    artwork: @Composable (Track, Modifier) -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(142.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            artwork(
                track,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
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

private fun homeGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "早上好，今天想听点什么？"
    in 12..17 -> "下午好，让音乐陪着你"
    in 18..22 -> "晚上好，放松一下吧"
    else -> "夜深了，听一首安静的歌"
}
