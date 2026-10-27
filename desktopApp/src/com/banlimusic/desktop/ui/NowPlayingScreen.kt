package com.banlimusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.shared.ui.components.BanLiBadge
import com.banlimusic.shared.ui.player.BanLiLyricsView
import com.banlimusic.shared.ui.screens.BanLiNowPlayingScreen
import com.banlimusic.shared.ui.screens.NowPlayingLayout
import com.banlimusic.shared.ui.screens.NowPlayingUiState
import kotlinx.coroutines.CancellationException

@Composable
fun DesktopNowPlayingScreen(state: DesktopAppState) {
    val playback by state.playback.collectAsState()
    val favorites by state.favorites.collectAsState()
    val folders by state.favoriteFolders.collectAsState()
    val settings by state.settings.collectAsState()
    val track = playback.track
    var section by remember(track?.id) { mutableIntStateOf(0) }

    BanLiNowPlayingScreen(
        state = NowPlayingUiState(
            track = track,
            title = track?.title,
            artist = track?.artistLine,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            isPlaying = playback.playing,
            isBuffering = playback.loading,
            hasNext = playback.index >= 0 && playback.index < playback.queue.lastIndex,
            hasPrevious = playback.index > 0,
        ),
        lyrics = null,
        lyricsLoading = false,
        lyricsError = null,
        layout = NowPlayingLayout.TWO_PANE,
        onBack = state::closePlayer,
        onSeek = { state.container.player.seekTo(it) },
        onReloadLyrics = {},
        backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
        artwork = { artworkModifier ->
            if (track != null) {
                DesktopTrackArtwork(
                    track = track,
                    loader = state.container.artworkLoader,
                    allowRemote = true,
                    modifier = artworkModifier,
                    cornerRadius = 18.dp,
                )
            }
        },
        controls = { controlsModifier ->
            if (track != null) {
                DesktopPlaybackControls(
                    state = state,
                    loading = playback.loading,
                    playing = playback.playing,
                    playModeLabel = playback.playMode.label,
                    modifier = controlsModifier,
                )
            }
        },
        metadata = if (track == null) {
            null
        } else {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    track.primarySource?.let { source ->
                        BanLiBadge(text = desktopProviderLabel(source.providerId))
                    }
                    BanLiBadge(text = "${settings.qualityKbps}K", highlighted = true)
                    folders[track.id]?.let { folder ->
                        BanLiBadge(text = folder, highlighted = true)
                    }
                }
            }
        },
        actions = if (playback.error == null) {
            null
        } else {
            {
                Text(
                    text = playback.error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        topBarActions = if (track == null) {
            null
        } else {
            {
                val isFavorite = favorites.any { it.id == track.id }
                TextButton(onClick = { state.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isFavorite) "已收藏" else "收藏")
                }
                TextButton(onClick = { state.download(track) }) {
                    Icon(
                        imageVector = DesktopIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("下载")
                }
            }
        },
        rightPanel = if (track == null) {
            null
        } else {
            {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = section == 0,
                                onClick = { section = 0 },
                                label = { Text("歌词") },
                            )
                            FilterChip(
                                selected = section == 1,
                                onClick = { section = 1 },
                                label = { Text("播放队列 (${playback.queue.size})") },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        if (section == 0) {
                            LyricsPanel(
                                state = state,
                                track = track,
                                positionMs = playback.positionMs,
                                durationMs = playback.durationMs,
                            )
                        } else {
                            QueuePanel(state = state, playback = playback)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DesktopPlaybackControls(
    state: DesktopAppState,
    loading: Boolean,
    playing: Boolean,
    playModeLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = state.container.player::previous) {
            Text("◀◀", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(6.dp))
        TextButton(
            onClick = state.container.player::togglePause,
            enabled = !loading,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text(if (playing) "暂停" else "播放", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = { state.container.player.next() }) {
            Text("▶▶", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = state.container.player::cyclePlayMode) {
            Text(playModeLabel, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = state.container.player::stop) {
            Text("停止", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LyricsPanel(
    state: DesktopAppState,
    track: Track,
    positionMs: Long,
    durationMs: Long,
) {
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track.id) { mutableStateOf(true) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(track.id) { mutableStateOf(0) }

    LaunchedEffect(track.id, reloadKey) {
        loading = true
        error = null
        try {
            lyrics = state.container.musicRepository.lyrics(track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            error = throwable.userMessage()
        } finally {
            loading = false
        }
    }

    BanLiLyricsView(
        lyrics = lyrics,
        loading = loading,
        error = error,
        positionMs = positionMs,
        durationMs = durationMs,
        onSeek = { state.container.player.seekTo(it) },
        onReload = { reloadKey++ },
        modifier = Modifier.fillMaxSize(),
        showHeader = false,
    )
}

@Composable
private fun QueuePanel(
    state: DesktopAppState,
    playback: com.banlimusic.desktop.player.DesktopPlaybackState,
) {
    if (playback.queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "播放队列为空",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(playback.queue, key = { _, track -> track.id }) { index, queueTrack ->
            val current = index == playback.index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { state.container.player.playAt(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = queueTrack.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = queueTrack.artistLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = TextNormalizer.formatDurationMs(queueTrack.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
