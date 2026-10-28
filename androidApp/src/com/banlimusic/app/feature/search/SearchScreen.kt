/**
 * 文件作用：搜索页的 Android 适配层，向共享搜索界面提供数据、批量操作与来源选择。
 */
package com.banlimusic.app.feature.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.feature.common.SourcePickerDialog
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.shared.ui.screens.BanLiSearchScreen
import com.banlimusic.shared.ui.screens.SearchUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onExitApp: () -> Unit = {},
    focusRequestKey: Int = 0,
    viewModel: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val enabledSources by viewModel.enabledSources.collectAsStateWithLifecycle()
    val favoriteCategories by viewModel.favoriteCategories.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultFavoriteCategory.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val preferredSources by viewModel.preferredSources.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sourcePickerTrack by remember { mutableStateOf<Track?>(null) }

    BackHandler {
        if (state.selectionMode) viewModel.clearSelection() else onExitApp()
    }
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
            favoriteIds = state.favoriteIds,
            favoriteFolders = state.favoriteFolders,
            downloadedIds = downloadedIds,
            preferredSources = preferredSources,
            categories = (favoriteCategories + defaultCategory).distinct(),
            defaultCategory = defaultCategory,
        ),
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onHistoryClick = viewModel::useHistory,
        onClearHistory = viewModel::clearHistory,
        artwork = { track, artworkModifier ->
            TrackArtwork(track = track, modifier = artworkModifier)
        },
        onPlayQueue = onPlayQueue,
        onToggleFavorite = viewModel::toggleFavorite,
        onSourceClick = { sourcePickerTrack = it },
        onAddFavorites = viewModel::addFavoritesTo,
        onDownload = viewModel::downloadTracks,
        onStartSelection = { viewModel.setSelectionMode(true) },
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onCancelSelection = viewModel::clearSelection,
        downloadQualities = { viewModel.downloadQualities(it) },
        modifier = Modifier.fillMaxSize(),
        searchIcon = Icons.Rounded.Search,
        clearIcon = Icons.Rounded.Close,
        autoFocus = false,
        focusRequestKey = focusRequestKey,
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
