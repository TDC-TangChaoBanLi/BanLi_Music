package com.banlimusic.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.shared.ui.components.BanLiMiniPlayer
import com.banlimusic.shared.ui.components.MiniPlayerUiState

/**
 * Desktop mini player adapter. The bar is always visible on main pages and
 * shows a placeholder before the first track is selected.
 */
@Composable
fun DesktopPlayerBar(state: DesktopAppState) {
    val playback by state.playback.collectAsState()
    val favorites by state.favorites.collectAsState()
    val track = playback.track
    BanLiMiniPlayer(
        state = MiniPlayerUiState(
            track = track,
            title = track?.title,
            subtitle = track?.artistLine,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            isPlaying = playback.playing,
            isBuffering = playback.loading,
            hasNext = playback.index in 0 until playback.queue.lastIndex,
            hasPrevious = playback.index > 0 || playback.queue.size > 1,
        ),
        artwork = { artworkModifier ->
            if (track != null) {
                DesktopTrackArtwork(
                    track = track,
                    loader = state.container.artworkLoader,
                    allowRemote = true,
                    modifier = artworkModifier,
                    cornerRadius = 9.dp,
                )
            }
        },
        onClick = state::openPlayer,
        onTogglePlayPause = { state.container.player.togglePause() },
        onPrevious = { state.container.player.previous() },
        onNext = { state.container.player.next() },
        onSeek = state.container.player::seekTo,
        isFavorite = track != null && favorites.any { it.id == track.id },
        onToggleFavorite = if (track == null) null else {
            { state.toggleFavorite(track) }
        },
        showPlaceholder = true,
        volume = playback.volume,
        onVolumeChange = state.container.player::setVolume,
        extraInfo = {
            when {
                playback.loading -> Text(
                    text = "正在解析播放地址…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                playback.error != null -> Text(
                    text = playback.error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                playback.queue.size > 1 -> Text(
                    text = "队列 ${playback.index + 1} / ${playback.queue.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
