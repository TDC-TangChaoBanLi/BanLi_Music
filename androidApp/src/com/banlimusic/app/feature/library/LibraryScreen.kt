/**
 * 文件作用：音乐库界面，包含收藏、最近播放、本地音乐与库内搜索。
 */
package com.banlimusic.app.feature.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.sort.AlphabetSection
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.feature.common.SourcePickerDialog
import com.banlimusic.app.ui.components.AlphabetOverlay
import com.banlimusic.app.ui.components.CompactTextButton
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.ListTabLabel
import com.banlimusic.app.ui.components.SelectionExtraAction
import com.banlimusic.app.ui.components.SortBar
import com.banlimusic.app.ui.components.rememberSelectionActionsState
import com.banlimusic.app.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.app.ui.components.TrackListHeaderSpec
import com.banlimusic.app.ui.components.TrackListPanel
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.app.ui.components.TrackScreenHeader
import com.banlimusic.app.ui.components.TrackSelectionActionBar
import com.banlimusic.app.ui.components.dragSelect
import com.banlimusic.app.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class LibraryTab(val label: String) {
    FAVORITES("收藏"),
    HISTORY("最近播放"),
    LOCAL("本地"),
}

private enum class LibrarySearchFilter(val label: String) {
    ALL("全部"),
    FAVORITES("收藏"),
    HISTORY("最近播放"),
    LOCAL("本地/下载"),
}

private enum class LocalFilter(val label: String) {
    ALL("全部"),
    IMPORTED("本地导入"),
    DOWNLOADED("已下载"),
}

private val FAVORITE_SORT_OPTIONS = listOf(TrackSort.TITLE, TrackSort.ARTIST, TrackSort.ALBUM)
private val HISTORY_SORT_OPTIONS = listOf(TrackSort.RECENT) + FAVORITE_SORT_OPTIONS
private val IMPORTED_SORT_OPTIONS = FAVORITE_SORT_OPTIONS
private val DOWNLOADED_SORT_OPTIONS = listOf(TrackSort.CREATED) + FAVORITE_SORT_OPTIONS

/** A-Z sections are computed over tracks; the list reserves one header item. */
private fun List<AlphabetSection>.withListHeaderOffset(): List<AlphabetSection> =
    map { it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(LibrarySearchFilter.ALL) }
    var searchCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var sourcePickerTrack by remember { mutableStateOf<Track?>(null) }
    var favoriteSort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    var favoriteAscending by rememberSaveable { mutableStateOf(true) }
    var historySort by rememberSaveable { mutableStateOf(TrackSort.RECENT) }
    var historyAscending by rememberSaveable { mutableStateOf(true) }

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val allFavorites by viewModel.allFavorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val importedTracks by viewModel.importedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteFolders by viewModel.favoriteFolders.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val preferredSources by viewModel.preferredSources.collectAsStateWithLifecycle()

    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var showImportOptions by rememberSaveable { mutableStateOf(false) }
    var localInfoTrack by remember { mutableStateOf<Track?>(null) }
    var localInfoLocation by remember { mutableStateOf("") }

    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { LibraryTab.entries.size })
    val folderCategories = remember(categories, defaultCategory) {
        (categories + defaultCategory).distinct()
    }

    val sortedFavorites = remember(favorites, favoriteSort, favoriteAscending) {
        TrackSorter.sort(favorites, favoriteSort, favoriteAscending)
    }
    val sortedHistory = remember(history, historySort, historyAscending) {
        TrackSorter.sort(history, historySort, historyAscending)
    }

    val normalizedSearch = remember(searchQuery) { TextNormalizer.normalize(searchQuery) }
    val searchResults = remember(
        normalizedSearch,
        searchFilter,
        searchCategory,
        allFavorites,
        history,
        localTracks,
        favoriteFolders,
    ) {
        if (normalizedSearch.isBlank()) {
            emptyList()
        } else {
            fun matches(track: Track): Boolean {
                val fields = listOfNotNull(
                    track.title,
                    track.album?.title,
                    track.artistLine,
                )
                return fields.any { TextNormalizer.normalize(it).contains(normalizedSearch) }
            }

            val filtered = when (searchFilter) {
                LibrarySearchFilter.ALL -> allFavorites + history + localTracks
                LibrarySearchFilter.FAVORITES -> allFavorites.filter { track ->
                    searchCategory == null ||
                        favoriteFolders[track.id]?.contains(searchCategory) == true
                }
                LibrarySearchFilter.HISTORY -> history
                LibrarySearchFilter.LOCAL -> localTracks
            }
            filtered.filter(::matches).distinctBy { it.id }
        }
    }
    val sortedSearch = remember(searchResults, favoriteSort, favoriteAscending) {
        TrackSorter.sort(searchResults, favoriteSort, favoriteAscending)
    }
    val searchSections = remember(sortedSearch, favoriteSort) {
        TrackSorter.sections(sortedSearch, favoriteSort).withListHeaderOffset()
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.importLocalMusic()
        } else {
            Toast.makeText(context, "需要媒体读取权限才能扫描本地音乐", Toast.LENGTH_SHORT).show()
        }
    }
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.importLocalFiles(uris)
    }
    val importFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importLocalFolder(uri)
        }
    }

    fun requestQuickImport() {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.importLocalMusic() else permissionLauncher.launch(permission)
    }

    fun exitSelection() {
        selectionMode = false
        viewModel.clearSelection()
    }

    BackHandler {
        when {
            selectionMode -> exitSelection()
            searchMode -> {
                searchMode = false
                searchQuery = ""
                keyboard?.hide()
            }
            else -> onExitApp()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        exitSelection()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TrackScreenHeader(title = "音乐库") {
            IconButton(
                onClick = {
                    if (searchMode) {
                        searchMode = false
                        searchQuery = ""
                        keyboard?.hide()
                    } else {
                        searchMode = true
                    }
                    exitSelection()
                },
            ) {
                Icon(
                    imageVector = if (searchMode) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchMode) "关闭搜索" else "搜索音乐库",
                )
            }
        }
        if (searchMode) {
            LibrarySearchContent(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                filter = searchFilter,
                onFilterChange = {
                    searchFilter = it
                    if (it != LibrarySearchFilter.FAVORITES) searchCategory = null
                },
                categories = folderCategories,
                selectedCategory = searchCategory,
                onCategoryChange = { searchCategory = it },
                results = sortedSearch,
                sections = searchSections,
                sort = favoriteSort,
                ascending = favoriteAscending,
                onSort = { favoriteSort = it },
                onToggleOrder = { favoriteAscending = !favoriteAscending },
                favoriteIds = favoriteIds,
                favoriteFolders = favoriteFolders,
                downloadedIds = downloadedIds,
                preferredSources = preferredSources,
                onSourceClick = { sourcePickerTrack = it },
                onPlayQueue = onPlayQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                defaultCategory = defaultCategory,
                downloadQualities = viewModel::downloadQualities,
                onStartSelection = { selectionMode = true },
                onToggleSelection = viewModel::toggleSelection,
                onSelectAll = { viewModel.selectAll(sortedSearch) },
                onCancelSelection = { exitSelection() },
                onAddFavorites = { tracks, folders ->
                    viewModel.addFavorites(tracks, folders)
                    exitSelection()
                },
                onDownloadSelected = { tracks, quality ->
                    viewModel.downloadSelected(tracks, quality)
                    exitSelection()
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { ListTabLabel(tab.label) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // Page swiping must not change folders while selecting tracks.
                userScrollEnabled = !selectionMode,
            ) { page ->
                when (LibraryTab.entries[page]) {
                    LibraryTab.FAVORITES -> FavoritesTabContent(
                        favorites = sortedFavorites,
                        categories = folderCategories,
                        selectedCategory = selectedCategory,
                        defaultCategory = defaultCategory,
                        favoriteFolders = favoriteFolders,
                        downloadedIds = downloadedIds,
                        preferredSources = preferredSources,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        sort = favoriteSort,
                        ascending = favoriteAscending,
                        onSort = { favoriteSort = it },
                        onToggleOrder = { favoriteAscending = !favoriteAscending },
                        onSelectCategory = { category ->
                            exitSelection()
                            viewModel.setCategory(category)
                        },
                        onCreateCategory = { showNewCategory = true },
                        onManageOrder = viewModel::saveCategoryOrder,
                        onPlayQueue = onPlayQueue,
                        onSourceClick = { sourcePickerTrack = it },
                        onSelectionModeChange = { enabled ->
                            selectionMode = enabled
                            if (!enabled) viewModel.clearSelection()
                        },
                        onToggleSelection = viewModel::toggleSelection,
                        onSelectAll = { viewModel.selectAll(sortedFavorites) },
                        onClearSelection = { exitSelection() },
                        onAddFavorites = { tracks, folders ->
                            viewModel.addFavorites(tracks, folders)
                            exitSelection()
                        },
                        onRemoveFromSelection = { tracks, folder ->
                            viewModel.removeFavorites(tracks, folder)
                            exitSelection()
                        },
                        onDeleteCategory = viewModel::deleteCategory,
                        onDownloadSelected = { tracks, quality ->
                            viewModel.downloadSelected(tracks, quality)
                            exitSelection()
                        },
                        downloadQualities = viewModel::downloadQualities,
                        modifier = Modifier.fillMaxSize(),
                    )

                    LibraryTab.HISTORY -> HistoryTabContent(
                        history = sortedHistory,
                        categories = folderCategories,
                        defaultCategory = defaultCategory,
                        favoriteFolders = favoriteFolders,
                        downloadedIds = downloadedIds,
                        preferredSources = preferredSources,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        sort = historySort,
                        ascending = historyAscending,
                        onSort = { historySort = it },
                        onToggleOrder = { historyAscending = !historyAscending },
                        onSelectionModeChange = { enabled ->
                            selectionMode = enabled
                            if (!enabled) viewModel.clearSelection()
                        },
                        onToggleSelection = viewModel::toggleSelection,
                        onSelectAll = { viewModel.selectAll(sortedHistory) },
                        onClearSelection = { exitSelection() },
                        onAddFavorites = { tracks, folders ->
                            viewModel.addFavorites(tracks, folders)
                            exitSelection()
                        },
                        onDownloadSelected = { tracks, quality ->
                            viewModel.downloadSelected(tracks, quality)
                            exitSelection()
                        },
                        downloadQualities = viewModel::downloadQualities,
                        onPlayQueue = onPlayQueue,
                        onSourceClick = { sourcePickerTrack = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onClearHistory = viewModel::clearHistory,
                        onDeleteHistory = viewModel::deleteHistorySelection,
                        modifier = Modifier.fillMaxSize(),
                    )

                    LibraryTab.LOCAL -> LocalTabContent(
                        importedTracks = importedTracks,
                        downloadedTracks = downloadedTracks,
                        downloadedBytes = downloadedBytes,
                        favoriteIds = favoriteIds,
                        favoriteFolders = favoriteFolders,
                        downloadedIds = downloadedIds,
                        categories = folderCategories,
                        defaultCategory = defaultCategory,
                        preferredSources = preferredSources,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onSelectionModeChange = { enabled ->
                            selectionMode = enabled
                            if (!enabled) viewModel.clearSelection()
                        },
                        onToggleSelection = viewModel::toggleSelection,
                        onSelectAll = { tracks -> viewModel.selectAll(tracks) },
                        onClearSelection = { exitSelection() },
                        onAddFavorites = { tracks, folders ->
                            viewModel.addFavorites(tracks, folders)
                            exitSelection()
                        },
                        onDownloadSelected = { tracks, quality ->
                            viewModel.downloadSelected(tracks, quality)
                            exitSelection()
                        },
                        onDeleteSelection = { tracks ->
                            viewModel.deleteLocalSelection(tracks)
                            exitSelection()
                        },
                        onDeleteDownloaded = { tracks ->
                            viewModel.removeDownloadedSelection(tracks)
                            exitSelection()
                        },
                        downloadQualities = viewModel::downloadQualities,
                        onPlayQueue = onPlayQueue,
                        onSourceClick = { sourcePickerTrack = it },
                        onRequestImport = { showImportOptions = true },
                        onShowLocation = { track ->
                            scope.launch {
                                localInfoLocation = withContext(Dispatchers.IO) {
                                    resolveLocalLocation(context, track)
                                }
                                localInfoTrack = track
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    sourcePickerTrack?.let { track ->
        SourcePickerDialog(
            track = track,
            preferredProviderId = preferredSources[track.id],
            onSelect = { providerId ->
                viewModel.selectSourcePreference(track.id, providerId)
                sourcePickerTrack = null
            },
            onDismiss = { sourcePickerTrack = null },
        )
    }

    if (showImportOptions) {
        AlertDialog(
            onDismissRequest = { showImportOptions = false },
            title = { Text("导入本地音乐") },
            text = {
                Column {
                    Text("自动识别会扫描系统媒体库，并过滤时长过短的提示音与铃声。")
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showImportOptions = false
                            requestQuickImport()
                        },
                    ) { Text("一键导入（自动识别歌曲）") }
                    TextButton(
                        onClick = {
                            showImportOptions = false
                            importFilesLauncher.launch(arrayOf("audio/*"))
                        },
                    ) { Text("选择文件导入") }
                    TextButton(
                        onClick = {
                            showImportOptions = false
                            importFolderLauncher.launch(null)
                        },
                    ) { Text("选择文件夹导入") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportOptions = false }) { Text("取消") }
            },
        )
    }

    if (showNewCategory) {
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("新建收藏夹") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("收藏夹名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCategoryName.isNotBlank(),
                    onClick = {
                        val name = newCategoryName.trim()
                        viewModel.createCategory(name)
                        viewModel.setCategory(name)
                        newCategoryName = ""
                        showNewCategory = false
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategory = false }) { Text("取消") }
            },
        )
    }

    localInfoTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { localInfoTrack = null },
            title = { Text(track.title) },
            text = { Text(localInfoLocation) },
            confirmButton = {
                TextButton(onClick = { localInfoTrack = null }) { Text("关闭") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: LibrarySearchFilter,
    onFilterChange: (LibrarySearchFilter) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit,
    results: List<Track>,
    sections: List<AlphabetSection>,
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    favoriteIds: Set<String>,
    favoriteFolders: Map<String, Set<String>>,
    downloadedIds: Set<String>,
    preferredSources: Map<String, String>,
    onSourceClick: (Track) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    defaultCategory: String,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onStartSelection: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    onAddFavorites: (List<Track>, Set<String>) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val selection = rememberSelectionActionsState(
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        tracks = results,
        defaultCategory = defaultCategory,
        downloadQualities = downloadQualities,
    )

    LaunchedEffect(Unit) {
        // Search is opened from the header icon, so focus the field immediately
        // and show the keyboard after the first layout pass.
        focusRequester.requestFocus()
        withFrameNanos { }
        keyboard?.show()
    }
    LaunchedEffect(query, filter, selectedCategory) {
        if (selectionMode) onCancelSelection()
        if (results.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("搜索歌名、专辑名或歌手名") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySearchFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
        if (filter == LibrarySearchFilter.FAVORITES) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategoryChange(null) },
                        label = { Text("全部收藏夹") },
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(category) },
                    )
                }
            }
        }
        SortBar(
            sort = sort,
            options = FAVORITE_SORT_OPTIONS,
            ascending = ascending,
            onSort = onSort,
            onToggleOrder = onToggleOrder,
        )
        when {
            query.isBlank() -> EmptyState(
                title = "搜索音乐库",
                description = "可搜索歌名、专辑名和歌手名，并按收藏、最近播放或本地/下载筛选。",
                icon = Icons.Rounded.Search,
            )

            results.isEmpty() -> EmptyState(
                title = "没有找到相关歌曲",
                description = "试试更短的关键词，或切换到其他筛选条件。",
                icon = Icons.Rounded.Search,
            )

            else -> TrackListPanel(
                items = results,
                itemKey = { it.id },
                validKey = { it.id },
                spec = TrackListHeaderSpec(
                    countText = "找到 ${results.size} 首",
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                ),
                listState = listState,
                sections = sections,
                ascending = ascending,
                selectionMode = selectionMode,
                onStartSelection = {
                    keyboard?.hide()
                    onStartSelection()
                },
                onSelect = onToggleSelection,
                onSelectAll = onSelectAll,
                onCancelSelection = onCancelSelection,
                onSort = {},
                onToggleOrder = {},
                emptyContent = {
                    EmptyState(
                        title = "没有找到相关歌曲",
                        description = "试试更短的关键词，或切换到其他筛选条件。",
                        icon = Icons.Rounded.Search,
                    )
                },
                modifier = Modifier.weight(1f),
                countActions = {
                    CompactTextButton(
                        text = "选择",
                        onClick = {
                            keyboard?.hide()
                            onStartSelection()
                        },
                    )
                },
                selectionBar = {
                    TrackSelectionActionBar(
                        categories = (categories + defaultCategory).distinct(),
                        state = selection,
                        onFavorite = { onAddFavorites(results, selection.chosenCategories) },
                        onDownload = { onDownloadSelected(results, selection.chosenQuality) },
                        favoriteEnabled = selectedIds.isNotEmpty() &&
                            selection.chosenCategories.isNotEmpty(),
                        downloadEnabled = selectedIds.isNotEmpty() &&
                            selection.qualities.isNotEmpty(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                },
            ) { index, track ->
                TrackRow(
                    track = track,
                    onClick = {
                        if (selectionMode) onToggleSelection(track.id)
                        else onPlayQueue(results, index)
                    },
                    isFavorite = track.id in favoriteIds,
                    onToggleFavorite = if (selectionMode) null else {
                        { onToggleFavorite(track) }
                    },
                    downloaded = track.id in downloadedIds,
                    categoryLabel = favoriteFolders[track.id]?.joinToString(" · "),
                    preferredProviderId = preferredSources[track.id],
                    onSourceClick = { onSourceClick(track) },
                    selected = if (selectionMode) track.id in selectedIds else null,
                )
            }
        }
    }
}

@Composable
private fun FavoritesTabContent(
    favorites: List<Track>,
    categories: List<String>,
    selectedCategory: String?,
    defaultCategory: String,
    favoriteFolders: Map<String, Set<String>>,
    downloadedIds: Set<String>,
    preferredSources: Map<String, String>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onCreateCategory: () -> Unit,
    onManageOrder: (List<String>) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onSourceClick: (Track) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, Set<String>) -> Unit,
    onRemoveFromSelection: (List<Track>, String?) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selection = rememberSelectionActionsState(
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        tracks = favorites,
        defaultCategory = defaultCategory,
        downloadQualities = downloadQualities,
    )
    var manageCategories by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }

    val spec = TrackListHeaderSpec(
        countText = "共 ${favorites.size} 首",
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        sort = sort,
        sortOptions = FAVORITE_SORT_OPTIONS,
        ascending = ascending,
        categories = categories,
        selectedCategory = selectedCategory,
    )
    val sections = remember(favorites, sort) {
        TrackSorter.sections(favorites, sort).withListHeaderOffset()
    }

    TrackListPanel(
        spec = spec,
        listState = listState,
        items = favorites,
        itemKey = { it.id },
        validKey = { it.id },
        sections = sections,
        ascending = ascending,
        selectionMode = selectionMode,
        onStartSelection = { onSelectionModeChange(true) },
        onSelect = onToggleSelection,
        onSelectAll = onSelectAll,
        onCancelSelection = onClearSelection,
        onSort = onSort,
        onToggleOrder = onToggleOrder,
        onCategory = onSelectCategory,
        countActions = {
            if (favorites.isNotEmpty()) {
                CompactTextButton(
                    text = "播放全部",
                    onClick = { onPlayQueue(favorites, 0) },
                )
            }
            CompactTextButton(
                text = "选择",
                onClick = { onSelectionModeChange(true) },
            )
        },
        categoryTrailing = {
            CompactTextButton(text = "+ 新建", onClick = onCreateCategory)
            CompactTextButton(text = "管理", onClick = { manageCategories = true })
        },
        emptyContent = {
            EmptyState(
                title = if (selectedCategory == null) "还没有收藏歌曲" else "这个收藏夹是空的",
                description = "在搜索结果或播放页收藏歌曲；默认收藏夹为“$defaultCategory”。",
                icon = Icons.Rounded.FavoriteBorder,
            )
        },
        modifier = modifier,
        selectionBar = {
            TrackSelectionActionBar(
                categories = (categories + defaultCategory).distinct(),
                state = selection,
                onFavorite = { onAddFavorites(favorites, selection.chosenCategories) },
                onDownload = { onDownloadSelected(favorites, selection.chosenQuality) },
                favoriteEnabled = selectedIds.isNotEmpty() && selection.chosenCategories.isNotEmpty(),
                downloadEnabled = selectedIds.isNotEmpty() && selection.qualities.isNotEmpty(),
                extraAction = SelectionExtraAction(
                    icon = Icons.Rounded.HeartBroken,
                    label = "取消收藏",
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        onRemoveFromSelection(
                            favorites.filter { it.id in selectedIds },
                            selectedCategory,
                        )
                    },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) { index, track ->
        TrackRow(
            track = track,
            onClick = {
                if (selectionMode) onToggleSelection(track.id)
                else onPlayQueue(favorites, index)
            },
            isFavorite = true,
            onToggleFavorite = null,
            showFavoriteState = !selectionMode,
            downloaded = track.id in downloadedIds,
            categoryLabel = favoriteFolders[track.id]?.joinToString(" · "),
            preferredProviderId = preferredSources[track.id],
            onSourceClick = { onSourceClick(track) },
            selected = if (selectionMode) track.id in selectedIds else null,
        )
    }

    if (manageCategories) {
        CategoryManagerDialog(
            categories = categories,
            protectedNames = setOf(DEFAULT_FAVORITE_CATEGORY, defaultCategory),
            onReorder = onManageOrder,
            onDelete = { pendingDeleteCategory = it },
            onDismiss = { manageCategories = false },
        )
    }

    pendingDeleteCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            title = { Text("删除收藏夹“$category”") },
            text = {
                Text(
                    "只会移除这个收藏夹和其中的归属关系，歌曲仍保留在其他收藏夹中。" +
                        "删除会在下次同步时同步到其他设备。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(category)
                        pendingDeleteCategory = null
                        if (selectedCategory == category) onSelectCategory(null)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryTabContent(
    history: List<Track>,
    categories: List<String>,
    defaultCategory: String,
    favoriteFolders: Map<String, Set<String>>,
    downloadedIds: Set<String>,
    preferredSources: Map<String, String>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, Set<String>) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onSourceClick: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistory: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selection = rememberSelectionActionsState(
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        tracks = history,
        defaultCategory = defaultCategory,
        downloadQualities = downloadQualities,
    )

    val spec = TrackListHeaderSpec(
        countText = "最近播放 ${history.size} 首",
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        sort = sort,
        sortOptions = HISTORY_SORT_OPTIONS,
        ascending = ascending,
        categories = null,
    )
    val sections = remember(history, sort) {
        TrackSorter.sections(history, sort).withListHeaderOffset()
    }

    TrackListPanel(
        spec = spec,
        listState = listState,
        items = history,
        itemKey = { it.id },
        validKey = { it.id },
        sections = sections,
        ascending = ascending,
        selectionMode = selectionMode,
        onStartSelection = { onSelectionModeChange(true) },
        onSelect = onToggleSelection,
        onSelectAll = onSelectAll,
        onCancelSelection = onClearSelection,
        onSort = onSort,
        onToggleOrder = onToggleOrder,
        countActions = {
            CompactTextButton(text = "清空记录", onClick = onClearHistory)
            if (history.isNotEmpty()) {
                CompactTextButton(
                    text = "选择",
                    onClick = { onSelectionModeChange(true) },
                )
            }
        },
        emptyContent = {
            EmptyState(
                title = "还没有播放记录",
                description = "播放过的歌曲会自动记录在这里。",
                icon = Icons.Rounded.History,
            )
        },
        modifier = modifier,
        selectionBar = {
            TrackSelectionActionBar(
                categories = (categories + defaultCategory).distinct(),
                state = selection,
                onFavorite = { onAddFavorites(history, selection.chosenCategories) },
                onDownload = { onDownloadSelected(history, selection.chosenQuality) },
                favoriteEnabled = selectedIds.isNotEmpty() && selection.chosenCategories.isNotEmpty(),
                downloadEnabled = selectedIds.isNotEmpty() && selection.qualities.isNotEmpty(),
                extraAction = SelectionExtraAction(
                    icon = Icons.Rounded.Delete,
                    label = "删除",
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { onDeleteHistory(history.filter { it.id in selectedIds }) },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) { index, track ->
        TrackRow(
            track = track,
            onClick = {
                if (selectionMode) onToggleSelection(track.id)
                else onPlayQueue(history, index)
            },
            isFavorite = track.id in favoriteFolders,
            showFavoriteState = false,
            onToggleFavorite = if (selectionMode) null else {
                { onToggleFavorite(track) }
            },
            downloaded = track.id in downloadedIds,
            categoryLabel = favoriteFolders[track.id]?.joinToString(" · "),
            preferredProviderId = preferredSources[track.id],
            onSourceClick = { onSourceClick(track) },
            selected = if (selectionMode) track.id in selectedIds else null,
        )
    }
}

@Composable
private fun LocalTabContent(
    importedTracks: List<Track>,
    downloadedTracks: List<Track>,
    downloadedBytes: Long,
    favoriteIds: Set<String>,
    favoriteFolders: Map<String, Set<String>>,
    downloadedIds: Set<String>,
    categories: List<String>,
    defaultCategory: String,
    preferredSources: Map<String, String>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: (List<Track>) -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, Set<String>) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    onDeleteSelection: (List<Track>) -> Unit,
    onDeleteDownloaded: (List<Track>) -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onSourceClick: (Track) -> Unit,
    onRequestImport: () -> Unit,
    onShowLocation: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var localFilter by rememberSaveable { mutableStateOf(LocalFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val tracks = remember(localFilter, importedTracks, downloadedTracks) {
        val merged = importedTracks + downloadedTracks
        when (localFilter) {
            LocalFilter.ALL -> merged.distinctBy { track ->
                track.sources.firstNotNullOfOrNull { it.localUri } ?: track.id
            }
            LocalFilter.IMPORTED -> importedTracks
            LocalFilter.DOWNLOADED -> downloadedTracks
        }
    }
    val sorted = remember(tracks, sort, ascending) {
        TrackSorter.sort(tracks, sort, ascending)
    }
    val sortOptions = if (localFilter == LocalFilter.DOWNLOADED) {
        DOWNLOADED_SORT_OPTIONS
    } else {
        IMPORTED_SORT_OPTIONS
    }
    val effectiveSort = if (sort in sortOptions) sort else sortOptions.first()
    val sections = remember(sorted, effectiveSort) {
        TrackSorter.sections(sorted, effectiveSort).withListHeaderOffset()
    }
    val selectedTracks = remember(sorted, selectedIds) {
        sorted.filter { it.id in selectedIds }
    }
    val selection = rememberSelectionActionsState(
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        tracks = sorted,
        defaultCategory = defaultCategory,
        downloadQualities = downloadQualities,
    )
    LaunchedEffect(localFilter) {
        onClearSelection()
    }

    val countText = when (localFilter) {
        LocalFilter.ALL -> "本地 ${importedTracks.size} 首 · 已下载 ${downloadedTracks.size} 首"
        LocalFilter.IMPORTED -> "本地 ${importedTracks.size} 首"
        LocalFilter.DOWNLOADED -> "已下载 ${downloadedTracks.size} 首 · 共 ${formatBytes(downloadedBytes)}"
    }
    val spec = TrackListHeaderSpec(
        countText = countText,
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        sort = effectiveSort,
        sortOptions = sortOptions,
        ascending = ascending,
        categories = listOf(LocalFilter.IMPORTED.label, LocalFilter.DOWNLOADED.label),
        selectedCategory = when (localFilter) {
            LocalFilter.ALL -> null
            LocalFilter.IMPORTED -> LocalFilter.IMPORTED.label
            LocalFilter.DOWNLOADED -> LocalFilter.DOWNLOADED.label
        },
    )

    TrackListPanel(
        spec = spec,
        listState = listState,
        items = sorted,
        itemKey = { it.id },
        validKey = { it.id },
        sections = sections,
        ascending = ascending,
        selectionMode = selectionMode,
        onStartSelection = { onSelectionModeChange(true) },
        onSelect = onToggleSelection,
        onSelectAll = { onSelectAll(sorted) },
        onCancelSelection = onClearSelection,
        onSort = { sort = it },
        onToggleOrder = { ascending = !ascending },
        onCategory = { category ->
            localFilter = when (category) {
                LocalFilter.IMPORTED.label -> LocalFilter.IMPORTED
                LocalFilter.DOWNLOADED.label -> LocalFilter.DOWNLOADED
                else -> LocalFilter.ALL
            }
        },
        countActions = {
            CompactTextButton(text = "导入", onClick = onRequestImport)
            if (sorted.isNotEmpty()) {
                CompactTextButton(
                    text = "选择",
                    onClick = { onSelectionModeChange(true) },
                )
            }
        },
        emptyContent = {
            EmptyState(
                title = "导入或下载音乐",
                description = "点击导入可一键扫描媒体库，或手动选择文件/文件夹；已下载完成的歌曲会自动加入这里。",
                icon = Icons.Rounded.LibraryMusic,
            )
        },
        modifier = modifier,
        selectionBar = {
            TrackSelectionActionBar(
                categories = (categories + defaultCategory).distinct(),
                state = selection,
                onFavorite = { onAddFavorites(sorted, selection.chosenCategories) },
                onDownload = { onDownloadSelected(sorted, selection.chosenQuality) },
                favoriteEnabled = selectedIds.isNotEmpty() && selection.chosenCategories.isNotEmpty(),
                downloadEnabled = selectedIds.isNotEmpty() && selection.qualities.isNotEmpty(),
                extraAction = SelectionExtraAction(
                    icon = Icons.Rounded.Delete,
                    label = "删除",
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        if (localFilter == LocalFilter.DOWNLOADED) {
                            onDeleteDownloaded(selectedTracks)
                        } else {
                            onDeleteSelection(selectedTracks)
                        }
                    },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) { index, track ->
        TrackRow(
            track = track,
            onClick = {
                if (selectionMode) onToggleSelection(track.id)
                else onPlayQueue(sorted, index)
            },
            isFavorite = track.id in favoriteIds,
            showFavoriteState = false,
            onToggleFavorite = null,
            downloaded = track.id in downloadedIds,
            categoryLabel = favoriteFolders[track.id]?.joinToString(" · "),
            preferredProviderId = preferredSources[track.id],
            onSourceClick = { onSourceClick(track) },
            infoAction = if (selectionMode) {
                null
            } else {
                { onShowLocation(track) }
            },
            selected = if (selectionMode) track.id in selectedIds else null,
        )
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<String>,
    protectedNames: Set<String>,
    onReorder: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember(categories) { mutableStateOf(categories) }
    var draggingName by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val rowHeight = with(LocalDensity.current) { 44.dp.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理收藏夹") },
        text = {
            if (categories.isEmpty()) {
                Text("还没有收藏夹")
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "长按拖动排序，默认收藏夹不可删除。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        working.forEach { category ->
                            val isDragging = draggingName == category
                            Surface(
                                color = if (isDragging) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "拖动排序",
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(24.dp)
                                            .pointerInput(category) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingName = category
                                                        dragDelta = 0f
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragDelta += amount.y
                                                        val index = working.indexOf(category)
                                                        if (index == -1) return@detectDragGesturesAfterLongPress
                                                        if (dragDelta > rowHeight / 2 &&
                                                            index < working.lastIndex
                                                        ) {
                                                            working = working.toMutableList().apply {
                                                                add(index + 1, removeAt(index))
                                                            }
                                                            dragDelta -= rowHeight
                                                        } else if (dragDelta < -rowHeight / 2 && index > 0) {
                                                            working = working.toMutableList().apply {
                                                                add(index - 1, removeAt(index))
                                                            }
                                                            dragDelta += rowHeight
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggingName = null
                                                        dragDelta = 0f
                                                        onReorder(working)
                                                    },
                                                    onDragCancel = {
                                                        draggingName = null
                                                        dragDelta = 0f
                                                        working = categories
                                                    },
                                                )
                                            },
                                    )
                                    Text(
                                        text = category,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (category !in protectedNames) {
                                        TextButton(onClick = { onDelete(category) }) {
                                            Text("删除")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun resolveLocalLocation(context: Context, track: Track): String {
    val uriString = track.sources.firstNotNullOfOrNull { it.localUri } ?: return "位置未知"
    val uri = Uri.parse(uriString)
    if (uri.scheme == "file") {
        return File(uri.path.orEmpty()).parent ?: uriString
    }
    return runCatching {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME,
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
            )
        }
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use uriString
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val relative = if (relativeIndex >= 0) cursor.getString(relativeIndex) else null
            val data = if (dataIndex >= 0) cursor.getString(dataIndex) else null
            when {
                !relative.isNullOrBlank() -> {
                    val directory = relative.trimEnd('/')
                    if (name.isNullOrBlank()) directory else "$directory/$name"
                }

                !data.isNullOrBlank() -> File(data).parent ?: data
                else -> uriString
            }
        } ?: uriString
    }.getOrDefault(uriString)
}
