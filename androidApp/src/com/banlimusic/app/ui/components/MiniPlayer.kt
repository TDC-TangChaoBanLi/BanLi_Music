/**
 * 文件作用：迷你播放条的 Android 适配层，把 Media3 状态映射到共享播放条。
 */
package com.banlimusic.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banlimusic.app.player.PlaybackUiState
import com.banlimusic.shared.ui.components.BanLiMiniPlayer
import com.banlimusic.shared.ui.components.MiniPlayerUiState

@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    BanLiMiniPlayer(
        state = MiniPlayerUiState(
            track = track,
            title = state.title,
            subtitle = state.artist,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            hasNext = state.hasNext,
            hasPrevious = state.hasPrevious,
        ),
        artwork = { artworkModifier ->
            TrackArtwork(
                track = track,
                artworkUrl = state.artworkUrl,
                modifier = artworkModifier,
                cornerRadius = 9.dp,
            )
        },
        onClick = onClick,
        onTogglePlayPause = onTogglePlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        modifier = modifier,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
    )
}
