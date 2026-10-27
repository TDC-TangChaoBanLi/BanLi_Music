package com.banlimusic.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.shared.ui.components.BanLiPlaybackBar

@Composable
fun DesktopPlayerBar(state: DesktopAppState) {
    val playback by state.playback.collectAsState()
    val track = playback.track ?: return
    BanLiPlaybackBar(
        title = track.title,
        subtitle = track.artistLine,
        progress = playback.progress,
        artwork = { artworkModifier ->
            DesktopTrackArtwork(
                track = track,
                loader = state.container.artworkLoader,
                allowRemote = true,
                modifier = artworkModifier,
                cornerRadius = 9.dp,
            )
        },
        onClick = state::openPlayer,
        controls = {
            TextButton(onClick = state.container.player::previous) {
                Text("◀◀", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = state.container.player::togglePause,
                enabled = !playback.loading,
            ) {
                Text(
                    text = if (playback.playing) "暂停" else "播放",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = { state.container.player.next() }) {
                Text("▶▶", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = state.container.player::cyclePlayMode) {
                Text(playback.playMode.label, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = state.container.player::stop) {
                Text("停止", style = MaterialTheme.typography.labelSmall)
            }
        },
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

@Composable
fun LegacyDesktopPlayerBar(state: DesktopAppState) {
    val playback by state.playback.collectAsState()
    val track = playback.track ?: return

    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopTrackArtwork(
                    track = track,
                    loader = state.container.artworkLoader,
                    allowRemote = true,
                    modifier = Modifier.size(54.dp).clickable(onClick = state::openPlayer),
                    cornerRadius = 9.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = state::openPlayer),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artistLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playback.loading) {
                        Text(
                            text = "正在解析播放地址…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (playback.error != null) {
                        Text(
                            text = playback.error.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (playback.queue.size > 1) {
                        Text(
                            text = "队列 ${playback.index + 1} / ${playback.queue.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = state.container.player::previous) {
                    Text("◀◀", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = state.container.player::togglePause,
                    enabled = !playback.loading,
                ) {
                    Text(
                        text = if (playback.playing) "暂停" else "▶ 播放",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = { state.container.player.next() }) {
                    Text("▶▶", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = state.container.player::stop) {
                    Text("停止", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlaybackTime(playback.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { playback.progress },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = formatPlaybackTime(playback.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
