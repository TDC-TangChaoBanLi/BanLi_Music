package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.banlimusic.shared.ui.screens.BanLiHomeScreen
import com.banlimusic.shared.ui.screens.HomeUiState

/** Desktop adapter for the shared home screen. */
@Composable
fun DesktopHomeScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()

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
        artwork = { track, anchorModifier ->
            DesktopTrackArtwork(
                track = track,
                loader = state.container.artworkLoader,
                allowRemote = settings.requestListArtwork,
                modifier = anchorModifier,
            )
        },
        modifier = Modifier.fillMaxSize(),
        searchIcon = Icons.Rounded.Search,
    )
}
