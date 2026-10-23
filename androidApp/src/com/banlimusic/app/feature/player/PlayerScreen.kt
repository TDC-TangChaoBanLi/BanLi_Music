package com.banlimusic.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.banlimusic.app.core.common.LrcLine
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.domain.playback.LrcParser
import com.banlimusic.app.domain.playback.stripTimestamps
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.player.PlaybackUiState
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.app.ui.components.formatPlaybackTime
import com.banlimusic.app.ui.components.providerLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val artworkUrl by viewModel.artworkUrl.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val track = state.currentTrack
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (track == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    TopBar(onBack = onBack)
                    EmptyState(
                        title = "还没有播放歌曲",
                        description = "从首页、搜索或音乐库选择一首歌开始播放。",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TopBar(onBack = onBack)
                    Spacer(Modifier.height(18.dp))
                    TrackArtwork(
                        track = track,
                        artworkUrl = artworkUrl ?: state.artworkUrl,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f),
                        cornerRadius = 26.dp,
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(
                        text = state.title ?: track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.artist ?: track.artistLine,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        track.primarySource?.let { source ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = providerLabel(source.providerId),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (track.sources.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${track.sources.size} 个来源",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    ProgressSection(state = state, onSeek = viewModel::seekTo)
                    Spacer(Modifier.height(10.dp))
                    ControlsSection(
                        state = state,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeatMode,
                    )
                    Spacer(Modifier.height(14.dp))
                    val canDownload = track.sources.any { TrackAction.DOWNLOAD in it.availableActions }
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (track.id in favoriteIds) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Rounded.FavoriteBorder
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(if (track.id in favoriteIds) "已收藏" else "收藏")
                        }
                        TextButton(
                            onClick = {
                                showLyrics = true
                                viewModel.ensureLyrics()
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Lyrics,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text("歌词")
                        }
                        TextButton(
                            onClick = viewModel::downloadCurrent,
                            enabled = canDownload,
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text("下载")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showLyrics) {
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            dragHandle = null,
        ) {
            LyricsSheet(
                lyricsState = lyricsState,
                playbackState = state,
                onReload = viewModel::reloadLyrics,
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "正在播放",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun ProgressSection(
    state: PlaybackUiState,
    onSeek: (Long) -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.positionMs, dragging, duration) {
        if (!dragging) {
            sliderValue = state.positionMs.toFloat().coerceIn(0f, duration.toFloat())
        }
    }

    Slider(
        value = sliderValue.coerceIn(0f, duration.toFloat()),
        onValueChange = {
            dragging = true
            sliderValue = it
        },
        onValueChangeFinished = {
            onSeek(sliderValue.toLong())
            dragging = false
        },
        valueRange = 0f..duration.toFloat(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatPlaybackTime(sliderValue.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatPlaybackTime(if (state.durationMs > 0) state.durationMs else 0L),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlsSection(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "随机播放",
                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(34.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = state.hasNext,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = "循环模式",
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun LyricsSheet(
    lyricsState: LyricsUiState,
    playbackState: PlaybackUiState,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
    ) {
        val lyrics: Lyrics? = lyricsState.lyrics
        var showTranslation by rememberSaveable { mutableStateOf(true) }
        val lines = remember(lyrics?.raw) { lyrics?.raw?.let(LrcParser::parse).orEmpty() }
        val translatedLines = remember(lyrics?.translated) {
            lyrics?.translated?.let(LrcParser::parse).orEmpty().associateBy { it.timeMs }
        }
        val hasTranslation = !lyrics?.translated.isNullOrBlank()
        val listState = rememberLazyListState()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "歌词",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (hasTranslation && !lyricsState.loading) {
                TextButton(onClick = { showTranslation = !showTranslation }) {
                    Text(if (showTranslation) "隐藏翻译" else "显示翻译")
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            lyricsState.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                }
            }

            lyricsState.error != null -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = lyricsState.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onReload) { Text("重试") }
                }
            }

            lyrics == null -> {
                EmptyState(
                    title = "暂无歌词",
                    description = "当前音乐源没有返回这首歌的歌词。",
                    icon = Icons.Rounded.Lyrics,
                )
            }

            lines.isEmpty() -> {
                Text(
                    text = lyrics.raw.stripTimestamps(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            else -> {
                val currentIndex = lines
                    .indexOfLast { it.timeMs <= playbackState.positionMs }
                    .coerceAtLeast(0)
                LaunchedEffect(currentIndex, showTranslation) {
                    if (currentIndex in lines.indices) {
                        runCatching { listState.animateScrollToItem(currentIndex) }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                ) {
                    itemsIndexed(lines) { index, line ->
                        val translated = translatedLines[line.timeMs]?.text
                        val text = if (showTranslation && !translated.isNullOrBlank()) {
                            translated
                        } else {
                            line.text
                        }
                        Text(
                            text = text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            textAlign = TextAlign.Center,
                            fontSize = if (index == currentIndex) 17.sp else 15.sp,
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == currentIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
