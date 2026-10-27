/**
 * 文件作用：首页的 Android 适配层，向共享首页提供数据、封面加载与播放动作。
 */
package com.banlimusic.app.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.shared.ui.screens.BanLiHomeScreen
import com.banlimusic.shared.ui.screens.HomeUiState

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()

    BanLiHomeScreen(
        state = HomeUiState(
            recentTracks = recent,
            favoriteTracks = favorites,
            enabledSourceIds = settings.enabledSources,
        ),
        onSearchClick = onSearchClick,
        onOpenLibrary = onOpenLibrary,
        onPlayQueue = onPlayQueue,
        artwork = { track, modifier ->
            TrackArtwork(
                track = track,
                modifier = modifier,
            )
        },
        modifier = Modifier.fillMaxSize(),
        searchIcon = Icons.Rounded.Search,
        favoriteRow = { track, index ->
            TrackRow(
                track = track,
                onClick = { onPlayQueue(favorites, index) },
            )
        },
    )
}
