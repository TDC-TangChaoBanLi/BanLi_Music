package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.shared.ui.components.rememberTrackSelectionState
import com.banlimusic.shared.ui.screens.BanLiSearchScreen
import com.banlimusic.shared.ui.screens.SearchUiState

/** Desktop adapter for the shared search screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopSearchScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val favorites by state.favorites.collectAsState()
    val favoriteFolders by state.favoriteFolders.collectAsState()
    val categories by state.favoriteCategories.collectAsState()
    val downloads by state.downloads.collectAsState()
    val searchHistory by state.searchHistory.collectAsState()
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    val downloadedIds = downloads
        .filter { it.status == DesktopDownloadStatus.COMPLETED }
        .mapTo(mutableSetOf()) { it.track.id }
    val search = state.search
    val selection = rememberTrackSelectionState()
    val folderCategories = remember(categories, settings.defaultFavoriteCategory) {
        (categories + settings.defaultFavoriteCategory).distinct()
    }

    LaunchedEffect(search.results) {
        selection.retain(search.results.mapTo(mutableSetOf()) { it.id })
    }

    val enabledSourceNames = SUPPORTED_SOURCES
        .filter { it.id in settings.enabledSources }
        .joinToString("、") { it.label }

    BanLiSearchScreen(
        state = SearchUiState(
            query = search.query,
            submittedQuery = search.submittedQuery,
            results = search.results,
            loading = search.loading,
            loadingMore = search.loadingMore,
            hasMore = search.hasMore,
            error = search.error,
            hasSearched = search.submittedQuery.isNotBlank(),
            history = searchHistory,
            enabledSourceNames = enabledSourceNames,
            selectionMode = selection.mode,
            selectedIds = selection.selectedIds,
            favoriteIds = favoriteIds,
            favoriteFolders = favoriteFolders,
            downloadedIds = downloadedIds,
            preferredSources = emptyMap(),
            categories = folderCategories,
            defaultCategory = settings.defaultFavoriteCategory,
            downloadQualityKbps = settings.downloadQualityKbps,
            supportsSourcePicker = false,
            immediateDragSelection = true,
        ),
        onQueryChange = state::updateQuery,
        onSubmit = state::submitSearch,
        onRetry = state::retrySearch,
        onLoadMore = state::loadMore,
        onHistoryClick = state::useSearchHistory,
        onClearHistory = state::clearSearchHistory,
        artwork = { track, artworkModifier ->
            DesktopTrackArtwork(
                track = track,
                loader = state.container.artworkLoader,
                allowRemote = settings.requestListArtwork,
                modifier = artworkModifier,
            )
        },
        onPlayQueue = { tracks, index ->
            if (tracks.isNotEmpty()) {
                val safe = index.coerceIn(tracks.indices)
                state.play(tracks[safe], tracks)
            }
        },
        onToggleFavorite = state::toggleFavorite,
        onSourceClick = {},
        onAddFavorites = state::addTracksToCategories,
        onDownload = { tracks, quality ->
            state.downloadTracks(
                tracks,
                quality ?: state.settings.value.downloadQualityKbps,
            )
        },
        onStartSelection = selection::enter,
        onToggleSelection = selection::toggle,
        onSelectAll = { selection.selectAll(search.results.map { it.id }) },
        onCancelSelection = selection::exit,
        downloadQualities = { DesktopSettingsRepository.DESKTOP_QUALITIES },
        modifier = Modifier.fillMaxSize(),
        searchIcon = Icons.Rounded.Search,
        clearIcon = Icons.Rounded.Close,
        autoFocus = true,
        searchFieldModifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                state.submitSearch()
                true
            } else {
                false
            }
        },
    )
}
