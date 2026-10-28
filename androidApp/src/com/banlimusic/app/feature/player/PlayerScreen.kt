/**
 * 文件作用：播放页的 Android 适配层，提供 Media3 状态、共享播放控件与来源选择。
 */
package com.banlimusic.app.feature.player

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.feature.common.SourcePickerDialog
import com.banlimusic.app.player.PlaybackUiState
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.shared.ui.components.BanLiPlaybackControls
import com.banlimusic.shared.ui.components.BanLiPlayMode
import com.banlimusic.shared.ui.components.BanLiPlayerActions
import com.banlimusic.shared.ui.components.BanLiPlayerMetadata
import com.banlimusic.shared.ui.screens.BanLiNowPlayingScreen
import com.banlimusic.shared.ui.screens.NowPlayingLayout
import com.banlimusic.shared.ui.screens.NowPlayingUiState
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val artworkUrl by viewModel.artworkUrl.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteCategories by viewModel.favoriteCategories.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val preferredSources by viewModel.preferredSources.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activeTrack = state.currentTrack
    val configuration = LocalConfiguration.current
    val twoPane = configuration.smallestScreenWidthDp >= 600 &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var downloadQuality by rememberSaveable(appSettings.downloadQualityKbps) {
        mutableIntStateOf(appSettings.downloadQualityKbps)
    }
    var downloadQualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sourcePickerTrack by remember { mutableStateOf<Track?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(activeTrack?.id) {
        downloadQualities = if (activeTrack == null) {
            emptyList()
        } else {
            viewModel.downloadQualities()
        }
        if (downloadQuality !in downloadQualities) {
            downloadQuality = downloadQualities.firstOrNull() ?: 0
        }
    }
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (twoPane || viewModel.lyrics.value.requested) viewModel.setLyricsVisible(true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BanLiNowPlayingScreen(
            state = NowPlayingUiState(
                track = activeTrack,
                title = state.title,
                artist = state.artist,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                hasNext = state.hasNext,
                hasPrevious = state.hasPrevious,
            ),
            lyrics = lyricsState.lyrics,
            lyricsLoading = lyricsState.loading,
            lyricsError = lyricsState.error,
            layout = if (twoPane) NowPlayingLayout.TWO_PANE else NowPlayingLayout.PAGER,
            onBack = onBack,
            onSeek = viewModel::seekTo,
            onReloadLyrics = viewModel::reloadLyrics,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            artwork = { artworkModifier ->
                if (activeTrack != null) {
                    TrackArtwork(
                        track = activeTrack,
                        artworkUrl = artworkUrl ?: state.artworkUrl,
                        requestRemoteArtwork = true,
                        modifier = artworkModifier,
                        cornerRadius = 22.dp,
                    )
                }
            },
            controls = { controlModifier ->
                BanLiPlaybackControls(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    hasNext = state.hasNext,
                    hasPrevious = state.hasPrevious,
                    playMode = state.playMode(),
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onCyclePlayMode = viewModel::cyclePlayMode,
                    modifier = controlModifier,
                )
            },
            metadata = if (activeTrack == null) {
                null
            } else {
                {
                    BanLiPlayerMetadata(
                        track = activeTrack,
                        preferredProviderId = preferredSources[activeTrack.id],
                        onSourceClick = { sourcePickerTrack = activeTrack },
                    )
                }
            },
            actions = if (activeTrack == null) {
                null
            } else {
                {
                    BanLiPlayerActions(
                        isFavorite = activeTrack.id in favoriteIds,
                        favoriteCategories = (
                            favoriteCategories + appSettings.defaultFavoriteCategory
                            ).distinct(),
                        canDownload = activeTrack.sources.any {
                            com.banlimusic.app.core.model.TrackAction.DOWNLOAD in
                                it.availableActions
                        },
                        downloadQualities = downloadQualities,
                        chosenQuality = downloadQuality.takeIf { it in downloadQualities },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToCategory = viewModel::addFavoriteToCategory,
                        onAddCategory = viewModel::addFavoriteToCategory,
                        onDownload = {
                            viewModel.downloadCurrent(
                                downloadQuality.takeIf { it in downloadQualities },
                            )
                        },
                        onDownloadQualityChange = { downloadQuality = it },
                    )
                }
            },
            initialShowLyrics = appSettings.defaultShowLyrics,
            onLyricsVisible = viewModel::setLyricsVisible,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    sourcePickerTrack?.let { track ->
        SourcePickerDialog(
            track = track,
            preferredProviderId = preferredSources[track.id],
            onSelect = { providerId ->
                viewModel.selectSource(providerId)
                sourcePickerTrack = null
            },
            onDismiss = { sourcePickerTrack = null },
        )
    }
}

private fun PlaybackUiState.playMode(): BanLiPlayMode = when {
    repeatMode == Player.REPEAT_MODE_ONE -> BanLiPlayMode.REPEAT_ONE
    shuffleEnabled -> BanLiPlayMode.SHUFFLE
    repeatMode == Player.REPEAT_MODE_ALL -> BanLiPlayMode.REPEAT_ALL
    else -> BanLiPlayMode.SEQUENTIAL
}
