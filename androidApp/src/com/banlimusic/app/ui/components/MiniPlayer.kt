package com.banlimusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.player.PlaybackUiState

@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(
                    track = track,
                    artworkUrl = state.artworkUrl,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 9.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title ?: track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist ?: track.artistLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = state.hasNext,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
