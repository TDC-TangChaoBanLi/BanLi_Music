/**
 * 文件作用：搜索页的 Android 适配层，提供多选拖拽、收藏/下载操作与平台对话框。
 */
package com.banlimusic.app.feature.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.feature.common.SourcePickerDialog
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.shared.ui.components.CompactTextButton
import com.banlimusic.shared.ui.components.TrackListHeaderSpec
import com.banlimusic.shared.ui.components.TrackSelectionActionBar
import com.banlimusic.shared.ui.components.dragSelect
import com.banlimusic.shared.ui.components.rememberSelectionActionsState
import com.banlimusic.shared.ui.components.trackListHeader
import com.banlimusic.shared.ui.screens.BanLiSearchScreen
import com.banlimusic.shared.ui.screens.SearchUiState

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler {
        if (state.selectionMode) viewModel.clearSelection() else onExitApp()
    }
    val history by viewModel.history.collectAsStateWithLifecycle()
    val enabledSources by viewModel.enabledSources.collectAsStateWithLifecycle()
    val favoriteCategories by viewModel.favoriteCategories.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultFavoriteCategory.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val preferredSources by viewModel.preferredSources.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val validIds = remember(state.results) { state.results.map { it.id }.toSet() }
    var sourcePickerTrack by remember { mutableStateOf<Track?>(null) }
    val selection = rememberSelectionActionsState(
        selectionMode = state.selectionMode,
        selectedIds = state.selectedIds,
        tracks = state.results,
        defaultCategory = defaultCategory,
        downloadQualities = { viewModel.downloadQualities(it) },
    )

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val enabledSourceNames = SUPPORTED_SOURCES
        .filter { it.id in enabledSources }
        .joinToString("、") { it.label }

    BanLiSearchScreen(
        state = SearchUiState(
            query = state.query,
            results = state.results,
            loading = state.isSearching,
            loadingMore = state.isLoadingMore,
            hasMore = state.hasMore,
            error = state.error,
            hasSearched = state.hasSearched,
            history = history,
            enabledSourceNames = enabledSourceNames,
            selectionMode = state.selectionMode,
            selectedIds = state.selectedIds,
        ),
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onHistoryClick = viewModel::useHistory,
        onClearHistory = viewModel::clearHistory,
        searchIcon = Icons.Rounded.Search,
        clearIcon = Icons.Rounded.Close,
        listState = listState,
        listModifier = Modifier.dragSelect(
            enabled = true,
            listState = listState,
            validKeys = validIds,
            onSelect = { id ->
                if (!state.selectionMode) viewModel.setSelectionMode(true)
                viewModel.toggleSelection(id)
            },
        ),
        header = {
            trackListHeader(
                spec = TrackListHeaderSpec(
                    countText = "找到 ${state.results.size} 首",
                    selectionMode = state.selectionMode,
                    selectedCount = state.selectedIds.size,
                ),
                onSelectAll = viewModel::selectAll,
                onCancelSelection = viewModel::clearSelection,
                countActions = {
                    if (state.error != null) {
                        Text(
                            text = "部分来源加载失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    CompactTextButton(
                        text = "选择",
                        onClick = { viewModel.setSelectionMode(true) },
                    )
                },
            )
        },
        selectionBar = if (state.selectionMode) {
            {
                TrackSelectionActionBar(
                    categories = (favoriteCategories + defaultCategory).distinct(),
                    state = selection,
                    onFavorite = { viewModel.batchFavorite(selection.chosenCategories) },
                    onDownload = { viewModel.batchDownload(selection.chosenQuality) },
                    favoriteEnabled = state.selectedIds.isNotEmpty() &&
                        selection.chosenCategories.isNotEmpty(),
                    downloadEnabled = state.selectedIds.isNotEmpty() &&
                        selection.qualities.isNotEmpty(),
                )
            }
        } else {
            null
        },
        trackRow = { track, index, selected ->
            TrackRow(
                track = track,
                onClick = {
                    if (state.selectionMode) {
                        viewModel.toggleSelection(track.id)
                    } else {
                        onPlayQueue(state.results, index)
                    }
                },
                isFavorite = track.id in state.favoriteIds,
                downloaded = track.id in downloadedIds,
                categoryLabel = state.favoriteFolders[track.id]?.joinToString(" · "),
                preferredProviderId = preferredSources[track.id],
                onSourceClick = { sourcePickerTrack = track },
                onToggleFavorite = if (state.selectionMode) {
                    null
                } else {
                    { viewModel.toggleFavorite(track) }
                },
                selected = if (state.selectionMode) selected else null,
            )
        },
    )

    sourcePickerTrack?.let { track ->
        SourcePickerDialog(
            track = track,
            preferredProviderId = preferredSources[track.id],
            onSelect = { providerId ->
                viewModel.setPreferredSource(track.id, providerId)
                sourcePickerTrack = null
            },
            onDismiss = { sourcePickerTrack = null },
        )
    }
}
