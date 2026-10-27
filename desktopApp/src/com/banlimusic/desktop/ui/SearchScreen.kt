package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.shared.ui.components.NewCategoryDialog
import com.banlimusic.shared.ui.components.SelectionExtraAction
import com.banlimusic.shared.ui.components.TrackSelectionActionBar
import com.banlimusic.shared.ui.components.rememberSelectionActionsState
import com.banlimusic.shared.ui.components.rememberTrackSelectionState
import com.banlimusic.shared.ui.screens.BanLiSearchScreen
import com.banlimusic.shared.ui.screens.SearchUiState

@Composable
fun DesktopSearchScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val playback by state.playback.collectAsState()
    val favoriteIds = state.favorites.collectAsState().value.mapTo(mutableSetOf()) { it.id }
    val favoriteFolders by state.favoriteFolders.collectAsState()
    val categories by state.favoriteCategories.collectAsState()
    val downloads by state.downloads.collectAsState()
    val searchHistory by state.searchHistory.collectAsState()
    val downloadedIds = downloads
        .filter { it.status == DesktopDownloadStatus.COMPLETED }
        .mapTo(mutableSetOf()) { it.track.id }
    val search = state.search
    val selection = rememberTrackSelectionState()
    val actionsState = rememberSelectionActionsState(
        selectionMode = selection.mode,
        selectedIds = selection.selectedIds,
        tracks = search.results,
        defaultCategory = settings.defaultFavoriteCategory,
        downloadQualities = { DesktopSettingsRepository.DESKTOP_QUALITIES },
    )
    var showNewCategory by remember { mutableStateOf(false) }
    val selectedTracks = search.results.filter { it.id in selection.selectedIds }

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
        ),
        onQueryChange = state::updateQuery,
        onSubmit = state::submitSearch,
        onRetry = state::retrySearch,
        onLoadMore = state::loadMore,
        onHistoryClick = state::useSearchHistory,
        onClearHistory = state::clearSearchHistory,
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
        header = {
            if (search.results.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "“${search.submittedQuery}” 共 ${search.results.size} 首",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                if (selection.mode) selection.exit() else selection.enter()
                            },
                        ) {
                            Text(if (selection.mode) "取消选择" else "选择")
                        }
                        Text(
                            text = "第 ${search.page} 页",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        selectionBar = if (selection.mode) {
            {
                TrackSelectionActionBar(
                    categories = categories,
                    state = actionsState,
                    onFavorite = {
                        val categoriesToAdd = actionsState.chosenCategories.ifEmpty {
                            setOf(settings.defaultFavoriteCategory)
                        }
                        categoriesToAdd.forEach { category ->
                            state.addTracksToCategory(selectedTracks, category)
                        }
                        selection.exit()
                    },
                    onDownload = {
                        state.downloadTracks(
                            selectedTracks,
                            actionsState.chosenQuality ?: settings.downloadQualityKbps,
                        )
                        selection.exit()
                    },
                    favoriteEnabled = selectedTracks.isNotEmpty() &&
                        actionsState.chosenCategories.isNotEmpty(),
                    downloadEnabled = selectedTracks.isNotEmpty() &&
                        actionsState.qualities.isNotEmpty(),
                    extraAction = SelectionExtraAction(
                        icon = Icons.Rounded.Add,
                        label = "新建",
                        onClick = { showNewCategory = true },
                    ),
                )
            }
        } else {
            null
        },
        trackRow = { track, _, selected ->
            DesktopTrackRow(
                track = track,
                loader = state.container.artworkLoader,
                allowRemoteArtwork = settings.requestListArtwork,
                favorite = track.id in favoriteIds,
                current = playback.track?.id == track.id,
                onPlay = {
                    if (selection.mode) selection.toggle(track.id)
                    else state.play(track, search.results)
                },
                onToggleFavorite = { state.toggleFavorite(track) },
                onDownload = { state.download(track) },
                downloaded = track.id in downloadedIds,
                categoryLabel = favoriteFolders[track.id],
                selected = if (selection.mode) selected else null,
            )
        },
    )

    if (showNewCategory) {
        NewCategoryDialog(
            onDismiss = { showNewCategory = false },
            onCreate = { name ->
                showNewCategory = false
                state.createFavoriteCategory(name) { category ->
                    state.addTracksToCategory(selectedTracks, category)
                    selection.exit()
                }
            },
        )
    }
}
