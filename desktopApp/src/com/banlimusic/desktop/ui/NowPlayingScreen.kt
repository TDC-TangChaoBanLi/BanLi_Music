package com.banlimusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.common.LrcLine
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.playback.LrcParser
import com.banlimusic.app.domain.playback.stripTimestamps
import kotlinx.coroutines.CancellationException

@Composable
fun DesktopNowPlayingScreen(state: DesktopAppState) {
    val playback by state.playback.collectAsState()
    val favorites by state.favorites.collectAsState()
    val settings by state.settings.collectAsState()
    val track = playback.track ?: return
    val isFavorite = favorites.any { it.id == track.id }
    var section by remember(track.id) { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = state::closePlayer) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
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
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("下载")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.15f))
                DesktopTrackArtwork(
                    track = track,
                    loader = state.container.artworkLoader,
                    allowRemote = true,
                    modifier = Modifier.size(310.dp),
                    cornerRadius = 18.dp,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = track.artistLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                track.album?.title?.takeIf { it.isNotBlank() }?.let { album ->
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    track.primarySource?.let { source ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                text = desktopProviderLabel(source.providerId),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${settings.qualityKbps}K",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.weight(0.12f))
                ProgressSection(state = state, playback = playback)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = state.container.player::previous) {
                        Text("◀◀", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(
                        onClick = state.container.player::togglePause,
                        enabled = !playback.loading,
                    ) {
                        if (playback.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            if (!playback.playing) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(if (playback.playing) "暂停" else "播放")
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { state.container.player.next() }) {
                        Text("▶▶", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = state.container.player::cyclePlayMode) {
                        Text(playback.playMode.label)
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = state.container.player::stop) {
                        Text("停止")
                    }
                }
                playback.error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(0.08f))
            }

            Surface(
                modifier = Modifier.width(430.dp).fillMaxHeight(),
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
                        LyricsPanel(state = state, track = track, positionMs = playback.positionMs)
                    } else {
                        QueuePanel(state = state, playback = playback)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(
    state: DesktopAppState,
    playback: com.banlimusic.desktop.player.DesktopPlaybackState,
) {
    var dragging by remember(playback.track?.id) { mutableStateOf(false) }
    var dragPosition by remember(playback.track?.id) { mutableFloatStateOf(0f) }
    val duration = playback.durationMs.coerceAtLeast(0L)
    val displayed = if (dragging) dragPosition else playback.positionMs.toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayed.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
            onValueChange = {
                dragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                dragging = false
                state.container.player.seekTo(dragPosition.toLong())
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = duration > 0L && !playback.loading,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlaybackTime(displayed.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatPlaybackTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LyricsPanel(state: DesktopAppState, track: Track, positionMs: Long) {
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track.id) { mutableStateOf(true) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(track.id) {
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

    val lines = remember(lyrics?.raw) {
        lyrics?.raw?.let(LrcParser::parse).orEmpty()
    }
    val translated = remember(lyrics?.translated) {
        lyrics?.translated?.let(LrcParser::parse).orEmpty().associateBy(LrcLine::timeMs)
    }
    val activeIndex = if (lines.isEmpty()) {
        -1
    } else {
        lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            runCatching { listState.animateScrollToItem(activeIndex) }
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }

        error != null -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        lines.isEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = lyrics?.raw?.stripTimestamps().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == activeIndex
                Column {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    translated[line.timeMs]?.let { translation ->
                        Text(
                            text = translation.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
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
                    text = com.banlimusic.app.core.common.TextNormalizer
                        .formatDurationMs(queueTrack.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
