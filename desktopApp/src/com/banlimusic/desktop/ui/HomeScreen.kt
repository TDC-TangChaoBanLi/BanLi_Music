package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.shared.ui.screens.BanLiHomeScreen
import com.banlimusic.shared.ui.screens.HomeUiState

@Composable
fun DesktopHomeScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()
    val folders by state.favoriteFolders.collectAsState()
    val downloads by state.downloads.collectAsState()
    val playback by state.playback.collectAsState()
    val downloadedIds = downloads
        .filter { it.status == DesktopDownloadStatus.COMPLETED }
        .mapTo(mutableSetOf()) { it.track.id }

    BanLiHomeScreen(
        state = HomeUiState(
            recentTracks = history,
            favoriteTracks = favorites,
            enabledSourceIds = settings.enabledSources,
        ),
        onSearchClick = state::openSearch,
        onOpenLibrary = { state.selectTab(DesktopTab.LIBRARY) },
        onPlayQueue = { tracks, index ->
            if (tracks.isNotEmpty()) {
                val safeIndex = index.coerceIn(tracks.indices)
                state.play(tracks[safeIndex], tracks)
            }
        },
        artwork = { track, modifier ->
            DesktopTrackArtwork(
                track = track,
                loader = state.container.artworkLoader,
                allowRemote = settings.requestListArtwork,
                modifier = modifier,
            )
        },
        modifier = Modifier.fillMaxSize(),
        searchIcon = Icons.Rounded.Search,
        recentTrackLimit = 10,
        favoriteRow = { track, _ ->
            DesktopTrackRow(
                track = track,
                loader = state.container.artworkLoader,
                allowRemoteArtwork = settings.requestListArtwork,
                favorite = true,
                current = playback.track?.id == track.id,
                onPlay = { state.play(track, favorites) },
                onToggleFavorite = { state.toggleFavorite(track) },
                onDownload = { state.download(track) },
                showFavorite = false,
                downloaded = track.id in downloadedIds,
                categoryLabel = folders[track.id],
            )
        },
    )
}
