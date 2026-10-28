package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.desktop.player.DesktopPlayMode
import com.banlimusic.shared.ui.components.BanLiPlaybackControls
import com.banlimusic.shared.ui.components.BanLiPlayMode
import com.banlimusic.shared.ui.components.BanLiPlayerActions
import com.banlimusic.shared.ui.components.BanLiPlayerMetadata
import com.banlimusic.shared.ui.screens.BanLiNowPlayingScreen
import com.banlimusic.shared.ui.screens.NowPlayingLayout
import com.banlimusic.shared.ui.screens.NowPlayingUiState
import kotlinx.coroutines.CancellationException

/** Desktop player adapter; layout and controls are shared with Android. */
@Composable
fun DesktopNowPlayingScreen(state: DesktopAppState) {
    val playback by state.playback.collectAsState()
    val favorites by state.favorites.collectAsState()
    val categories by state.favoriteCategories.collectAsState()
    val settings by state.settings.collectAsState()
    val track = playback.track
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember(track?.id) { mutableStateOf(track != null) }
    var lyricsError by remember(track?.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(track?.id) { mutableIntStateOf(0) }
    var downloadQuality by remember(track?.id, settings.downloadQualityKbps) {
        mutableIntStateOf(settings.downloadQualityKbps)
    }

    LaunchedEffect(track?.id, reloadKey) {
        if (track == null) return@LaunchedEffect
        lyricsLoading = true
        lyricsError = null
        try {
            lyrics = state.container.musicRepository.lyrics(track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            lyricsError = throwable.userMessage()
        } finally {
            lyricsLoading = false
        }
    }

    BanLiNowPlayingScreen(
        state = NowPlayingUiState(
            track = track,
            title = track?.title,
            artist = track?.artistLine,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            isPlaying = playback.playing,
            isBuffering = playback.loading,
            hasNext = playback.index in 0 until playback.queue.lastIndex,
            hasPrevious = playback.index > 0 || playback.queue.size > 1,
        ),
        lyrics = lyrics,
        lyricsLoading = lyricsLoading,
        lyricsError = lyricsError,
        layout = NowPlayingLayout.TWO_PANE,
        onBack = state::closePlayer,
        onSeek = { state.container.player.seekTo(it) },
        onReloadLyrics = { reloadKey++ },
        backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
        artwork = { artworkModifier ->
            if (track != null) {
                DesktopTrackArtwork(
                    track = track,
                    loader = state.container.artworkLoader,
                    allowRemote = true,
                    modifier = artworkModifier,
                    cornerRadius = 22.dp,
                )
            }
        },
        controls = { controlsModifier ->
            BanLiPlaybackControls(
                isPlaying = playback.playing,
                isBuffering = playback.loading,
                hasNext = playback.index in 0 until playback.queue.lastIndex,
                hasPrevious = playback.index > 0 || playback.queue.size > 1,
                playMode = playback.playMode.toSharedMode(),
                onTogglePlayPause = { state.container.player.togglePause() },
                onPrevious = { state.container.player.previous() },
                onNext = { state.container.player.next() },
                onCyclePlayMode = { state.container.player.cyclePlayMode() },
                modifier = controlsModifier,
                volume = playback.volume,
                onVolumeChange = state.container.player::setVolume,
            )
        },
        metadata = if (track == null) {
            null
        } else {
            {
                BanLiPlayerMetadata(track = track)
            }
        },
        actions = if (track == null) {
            null
        } else {
            {
                BanLiPlayerActions(
                    isFavorite = favorites.any { it.id == track.id },
                    favoriteCategories = (
                        categories + settings.defaultFavoriteCategory
                        ).distinct(),
                    canDownload = track.sources.any {
                        TrackAction.DOWNLOAD in it.availableActions
                    },
                    downloadQualities = DesktopSettingsRepository.DESKTOP_QUALITIES,
                    chosenQuality = downloadQuality.takeIf {
                        it in DesktopSettingsRepository.DESKTOP_QUALITIES
                    },
                    onToggleFavorite = { state.toggleFavorite(track) },
                    onAddToCategory = { category ->
                        state.addTracksToCategory(listOf(track), category)
                    },
                    onAddCategory = { category ->
                        state.createFavoriteCategory(category) { created ->
                            state.addTracksToCategory(listOf(track), created)
                        }
                    },
                    onDownload = { state.download(track, downloadQuality) },
                    onDownloadQualityChange = { downloadQuality = it },
                )
            }
        },
        initialShowLyrics = false,
    )
}

private fun DesktopPlayMode.toSharedMode(): BanLiPlayMode = when (this) {
    DesktopPlayMode.SEQUENTIAL -> BanLiPlayMode.SEQUENTIAL
    DesktopPlayMode.REPEAT_ALL -> BanLiPlayMode.REPEAT_ALL
    DesktopPlayMode.REPEAT_ONE -> BanLiPlayMode.REPEAT_ONE
    DesktopPlayMode.SHUFFLE -> BanLiPlayMode.SHUFFLE
}
