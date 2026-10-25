/**
 * 文件作用：全屏播放器界面，包含海报、歌词、收藏与下载操作。
 */
package com.banlimusic.app.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.banlimusic.app.core.common.LrcLine
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.domain.playback.LrcParser
import com.banlimusic.app.domain.playback.stripTimestamps
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.player.PlaybackUiState
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.app.ui.components.formatPlaybackTime
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.ui.components.sourceDisplayNameAnnotated
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
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
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(
        initialPage = if (appSettings.defaultShowLyrics) 1 else 0,
        pageCount = { 2 },
    )
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var downloadQuality by rememberSaveable(appSettings.downloadQualityKbps) {
        mutableStateOf(appSettings.downloadQualityKbps)
    }
    var downloadQualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    val track = state.currentTrack

    BackHandler { onBack() }

    LaunchedEffect(track?.id) {
        downloadQualities = if (track == null) emptyList() else viewModel.downloadQualities()
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
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.ensureLyrics()
    }
    LaunchedEffect(appSettings.defaultShowLyrics) {
        val target = if (appSettings.defaultShowLyrics) 1 else 0
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        if (target == 1) viewModel.ensureLyrics()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (track == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    TopBar(onBack = onBack)
                    EmptyState(
                        title = "还没有播放歌曲",
                        description = "从首页、搜索或音乐库选择一首歌开始播放。",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    TopBar(onBack = onBack)
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        if (page == 0) {
                            PosterPage(
                                track = track,
                                state = state,
                                artworkUrl = artworkUrl ?: state.artworkUrl,
                                isFavorite = track.id in favoriteIds,
                                favoriteCategories = (
                                    favoriteCategories + appSettings.defaultFavoriteCategory
                                    ).distinct(),
                                canDownload = track.sources.any {
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
                                onSeek = viewModel::seekTo,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onPrevious = viewModel::previous,
                                onNext = viewModel::next,
                                onCyclePlayMode = viewModel::cyclePlayMode,
                            )
                        } else {
                            LyricsPage(
                                lyricsState = lyricsState,
                                playbackState = state,
                                onSeek = viewModel::seekTo,
                                onReload = viewModel::reloadLyrics,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    PageIndicator(
                        pageCount = 2,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
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
private fun PosterPage(
    track: com.banlimusic.app.core.model.Track,
    state: PlaybackUiState,
    artworkUrl: String?,
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
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = LocalConfiguration.current.smallestScreenWidthDp >= 600
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(
                    track = track,
                    artworkUrl = artworkUrl,
                    requestRemoteArtwork = true,
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = 28.dp,
                )
                Spacer(Modifier.size(32.dp))
                PlayerDetails(
                    track = track,
                    state = state,
                    isFavorite = isFavorite,
                    favoriteCategories = favoriteCategories,
                    canDownload = canDownload,
                    downloadQuality = downloadQuality,
                    downloadQualities = downloadQualities,
                    onToggleFavorite = onToggleFavorite,
                    onAddFavorite = onAddFavorite,
                    onNewCategory = onNewCategory,
                    onDownload = onDownload,
                    onDownloadQualityChange = onDownloadQualityChange,
                    onSeek = onSeek,
                    onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onCyclePlayMode = onCyclePlayMode,
                    modifier = Modifier
                        .weight(1.1f)
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(10.dp))
                TrackArtwork(
                    track = track,
                    artworkUrl = artworkUrl,
                    requestRemoteArtwork = true,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .aspectRatio(1f),
                    cornerRadius = 26.dp,
                )
                Spacer(Modifier.height(20.dp))
                PlayerDetails(
                    track = track,
                    state = state,
                    isFavorite = isFavorite,
                    favoriteCategories = favoriteCategories,
                    canDownload = canDownload,
                    downloadQuality = downloadQuality,
                    downloadQualities = downloadQualities,
                    onToggleFavorite = onToggleFavorite,
                    onAddFavorite = onAddFavorite,
                    onNewCategory = onNewCategory,
                    onDownload = onDownload,
                    onDownloadQualityChange = onDownloadQualityChange,
                    onSeek = onSeek,
                    onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onCyclePlayMode = onCyclePlayMode,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "左滑查看歌词",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    track: com.banlimusic.app.core.model.Track,
    state: PlaybackUiState,
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
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var favoriteMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state.title ?: track.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.artist ?: track.artistLine,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
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
            if (track.sources.size > 1) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${track.sources.size} 个来源",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        ProgressSection(state = state, onSeek = onSeek)
        Spacer(Modifier.height(10.dp))
        ControlsSection(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onCyclePlayMode = onCyclePlayMode,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(5.dp))
                Text(if (isFavorite) "已收藏" else "收藏")
            }
            Box {
                IconButton(
                    onClick = { favoriteMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "选择收藏夹",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = favoriteMenu, onDismissRequest = { favoriteMenu = false }) {
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
                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
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
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "正在播放",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun ProgressSection(
    state: PlaybackUiState,
    onSeek: (Long) -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.positionMs, dragging, duration) {
        if (!dragging) {
            sliderValue = state.positionMs.toFloat().coerceIn(0f, duration.toFloat())
        }
    }

    Slider(
        value = sliderValue.coerceIn(0f, duration.toFloat()),
        onValueChange = {
            dragging = true
            sliderValue = it
        },
        onValueChangeFinished = {
            onSeek(sliderValue.toLong())
            dragging = false
        },
        valueRange = 0f..duration.toFloat(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatPlaybackTime(sliderValue.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatPlaybackTime(if (state.durationMs > 0) state.durationMs else 0L),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlsSection(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 随机播放 → 列表循环 → 单曲循环，固定在左侧以避免影响居中的传输控制键
        val singleRepeat = state.repeatMode == Player.REPEAT_MODE_ONE
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
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
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
private fun LyricsPage(
    lyricsState: LyricsUiState,
    playbackState: PlaybackUiState,
    onSeek: (Long) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyrics: Lyrics? = lyricsState.lyrics
    var showTranslation by rememberSaveable { mutableStateOf(true) }
    val lines = remember(lyrics?.raw) { lyrics?.raw?.let(LrcParser::parse).orEmpty() }
    val translatedLines = remember(lyrics?.translated) {
        lyrics?.translated?.let(LrcParser::parse).orEmpty().associateBy { it.timeMs }
    }
    val hasTranslation = !lyrics?.translated.isNullOrBlank()
    val listState = rememberLazyListState()
    val duration = playbackState.durationMs.coerceAtLeast(0L)
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var boxTopInWindow by remember { mutableFloatStateOf(0f) }
    var listTopInWindow by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .onGloballyPositioned { boxTopInWindow = it.positionInWindow().y }
            .pointerInput(lines) {
                fun updateTarget(windowY: Float) {
                    val listY = windowY - listTopInWindow
                    val visible = listState.layoutInfo.visibleItemsInfo
                    if (visible.isEmpty()) return
                    val item = visible.firstOrNull {
                        listY.toInt() in it.offset until (it.offset + it.size)
                    } ?: visible.minByOrNull {
                        abs(it.offset + it.size / 2 - listY)
                    } ?: return
                    val index = item.index
                    if (index in lines.indices) {
                        scrubIndex = index
                        scrubMs = lines[index].timeMs
                    }
                }
                coroutineScope {
                    val edgeThreshold = 72.dp.toPx()
                    val scrollStep = 12.dp.toPx()
                    var pointerWindowY = 0f
                    var autoScroll: Job? = null

                    fun stopAutoScroll() {
                        autoScroll?.cancel()
                        autoScroll = null
                    }

                    fun startAutoScroll() {
                        if (autoScroll != null) return
                        autoScroll = launch {
                            while (isActive) {
                                val info = listState.layoutInfo
                                val viewportTop = listTopInWindow + info.viewportStartOffset
                                val viewportBottom = listTopInWindow + info.viewportEndOffset
                                val delta = when {
                                    pointerWindowY < viewportTop + edgeThreshold -> -scrollStep
                                    pointerWindowY > viewportBottom - edgeThreshold -> scrollStep
                                    else -> 0f
                                }
                                if (delta == 0f) {
                                    stopAutoScroll()
                                    break
                                }
                                listState.scrollBy(delta)
                                updateTarget(pointerWindowY)
                                delay(16)
                            }
                        }
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            pointerWindowY = boxTopInWindow + offset.y
                            updateTarget(pointerWindowY)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pointerWindowY = boxTopInWindow + change.position.y
                            updateTarget(pointerWindowY)
                            val info = listState.layoutInfo
                            val viewportTop = listTopInWindow + info.viewportStartOffset
                            val viewportBottom = listTopInWindow + info.viewportEndOffset
                            val nearEdge = pointerWindowY < viewportTop + edgeThreshold ||
                                pointerWindowY > viewportBottom - edgeThreshold
                            if (nearEdge) startAutoScroll() else stopAutoScroll()
                        },
                        onDragEnd = {
                            stopAutoScroll()
                            scrubMs?.let(onSeek)
                            scrubIndex = null
                            scrubMs = null
                        },
                        onDragCancel = {
                            stopAutoScroll()
                            scrubIndex = null
                            scrubMs = null
                        },
                    )
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
        ) {
            val track = playbackState.currentTrack
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: playbackState.title ?: "未知歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        track?.album?.title?.takeIf { it.isNotBlank() },
                        track?.artistLine ?: playbackState.artist,
                    ).joinToString("·")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (hasTranslation && !lyricsState.loading) {
                    TextButton(onClick = { showTranslation = !showTranslation }) {
                        Text(if (showTranslation) "隐藏翻译" else "显示翻译")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                lyricsState.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }

                lyricsState.error != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = lyricsState.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onReload) { Text("重试") }
                    }
                }

                lyrics == null -> {
                    EmptyState(
                        title = "暂无歌词",
                        description = "当前音乐源没有返回这首歌的歌词。",
                        icon = Icons.Rounded.Lyrics,
                    )
                }

                lines.isEmpty() -> {
                    Text(
                        text = lyrics.raw.stripTimestamps(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                else -> {
                    val currentIndex = lines
                        .indexOfLast { it.timeMs <= playbackState.positionMs }
                        .coerceAtLeast(0)
                    val activeIndex = scrubIndex ?: currentIndex
                    LaunchedEffect(currentIndex, showTranslation, scrubIndex) {
                        if (scrubIndex == null && currentIndex in lines.indices) {
                            // Keep the active line on the third visible row.
                            val targetIndex = (currentIndex - 2).coerceAtLeast(0)
                            runCatching { listState.animateScrollToItem(targetIndex) }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = scrubIndex == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onGloballyPositioned { listTopInWindow = it.positionInWindow().y },
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val translated = translatedLines[line.timeMs]?.text
                            val text = if (showTranslation && !translated.isNullOrBlank()) {
                                translated
                            } else {
                                line.text
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (index == scrubIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(2.dp),
                                            ),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = text,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = if (index == activeIndex) 17.sp else 15.sp,
                                    fontWeight = if (index == activeIndex) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = if (index == activeIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        scrubMs?.let { target ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "${formatPlaybackTime(target)} / ${formatPlaybackTime(duration)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
