/**
 * 文件作用：播放页的 Android 适配层，提供 Media3 状态、图标控制与平台菜单。
 */
package com.banlimusic.app.feature.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.feature.common.SourcePickerDialog
import com.banlimusic.app.player.PlaybackUiState
import com.banlimusic.app.ui.components.NON_PROVIDER_SOURCE_IDS
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.ui.components.sourceDisplayNameAnnotated
import com.banlimusic.shared.ui.screens.BanLiNowPlayingScreen
import com.banlimusic.shared.ui.screens.NowPlayingLayout
import com.banlimusic.shared.ui.screens.NowPlayingUiState
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

    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var downloadQuality by rememberSaveable(appSettings.downloadQualityKbps) {
        mutableStateOf(appSettings.downloadQualityKbps)
    }
    var downloadQualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sourcePickerTrack by remember { mutableStateOf<com.banlimusic.app.core.model.Track?>(null) }

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
                AndroidPlaybackControls(
                    state = state,
                    modifier = controlModifier,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onCyclePlayMode = viewModel::cyclePlayMode,
                )
            },
            metadata = if (activeTrack == null) {
                null
            } else {
                {
                    AndroidTrackMetadata(
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
                    AndroidPlayerActions(
                        isFavorite = activeTrack.id in favoriteIds,
                        favoriteCategories = (
                            favoriteCategories + appSettings.defaultFavoriteCategory
                            ).distinct(),
                        canDownload = activeTrack.sources.any {
                            TrackAction.DOWNLOAD in it.availableActions
                        },
                        downloadQuality = downloadQuality,
                        downloadQualities = downloadQualities,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddFavorite = viewModel::addFavoriteToCategory,
                        onNewCategory = { showCategoryDialog = true },
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

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("新建收藏夹") },
            text = {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("收藏夹名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCategory.isNotBlank(),
                    onClick = {
                        val name = newCategory.trim()
                        viewModel.addFavoriteToCategory(name)
                        newCategory = ""
                        showCategoryDialog = false
                    },
                ) { Text("收藏") }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AndroidTrackMetadata(
    track: com.banlimusic.app.core.model.Track,
    preferredProviderId: String?,
    onSourceClick: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        track.primarySource?.let { source ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = sourceDisplayNameAnnotated(source.providerId),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (track.sources.count { it.providerId !in NON_PROVIDER_SOURCE_IDS } > 1) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable(onClick = onSourceClick),
            ) {
                Text(
                    text = "${track.sources.count { it.providerId !in NON_PROVIDER_SOURCE_IDS }} 个来源",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    if (preferredProviderId != null) {
        Text(
            text = "已选择来源：${sourceDisplayNameAnnotated(preferredProviderId)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AndroidPlaybackControls(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val singleRepeat = state.repeatMode == Player.REPEAT_MODE_ONE
    Box(modifier = modifier.fillMaxWidth()) {
        IconButton(
            onClick = onCyclePlayMode,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = when {
                    singleRepeat -> Icons.Rounded.RepeatOne
                    state.shuffleEnabled -> Icons.Rounded.Shuffle
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = when {
                    singleRepeat -> "单曲循环"
                    state.shuffleEnabled -> "随机播放"
                    else -> "列表循环"
                },
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
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
        }
    }
}

@Composable
private fun AndroidPlayerActions(
    isFavorite: Boolean,
    favoriteCategories: List<String>,
    canDownload: Boolean,
    downloadQuality: Int,
    downloadQualities: List<Int>,
    onToggleFavorite: () -> Unit,
    onAddFavorite: (String) -> Unit,
    onNewCategory: () -> Unit,
    onDownload: () -> Unit,
    onDownloadQualityChange: (Int) -> Unit,
) {
    var favoriteMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(5.dp))
                Text(if (isFavorite) "已收藏" else "收藏")
            }
            Box {
                IconButton(onClick = { favoriteMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "选择收藏夹",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = favoriteMenu,
                    onDismissRequest = { favoriteMenu = false },
                ) {
                    favoriteCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                favoriteMenu = false
                                onAddFavorite(category)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("新建收藏夹…") },
                        onClick = {
                            favoriteMenu = false
                            onNewCategory()
                        },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onDownload, enabled = canDownload) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(5.dp))
                Text("下载")
            }
            if (downloadQualities.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = { qualityMenu = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "选择下载音质",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false },
                    ) {
                        downloadQualities.forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(qualityLabel(quality)) },
                                onClick = {
                                    qualityMenu = false
                                    onDownloadQualityChange(quality)
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = if (downloadQualities.isEmpty()) {
                "下载音质：默认（该来源不提供选择）"
            } else {
                "下载音质：${downloadQuality.takeIf { it > 0 }?.let(::qualityLabel) ?: "默认"}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
