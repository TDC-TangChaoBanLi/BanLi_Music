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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.sort.AlphabetSection
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.AlphabetDragBubble
import com.banlimusic.app.ui.components.AlphabetIndex
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.app.ui.components.dragSelect
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.ui.components.qualityShortLabel
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(LibrarySearchFilter.ALL) }
    var searchCategory by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler {
        if (searchMode) searchMode = false else onExitApp()
    }
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val allFavorites by viewModel.allFavorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteFolders by viewModel.favoriteFolders.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var libraryHeaderVisible by remember { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var showImportOptions by rememberSaveable { mutableStateOf(false) }
    var localInfoTrack by remember { mutableStateOf<Track?>(null) }
    var localInfoLocation by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { LibraryTab.entries.size })
    val sortedFavorites = remember(favorites, sort, ascending) {
        TrackSorter.sort(favorites, sort, ascending)
    }
    val sortedHistory = remember(history, sort, ascending) {
        TrackSorter.sort(history, sort, ascending)
    }
    val sortedLocal = remember(localTracks, sort, ascending) {
        TrackSorter.sort(localTracks, sort, ascending)
    }
    val favoriteSections = remember(sortedFavorites, sort) {
        TrackSorter.sections(sortedFavorites, sort)
    }
    val historySections = remember(sortedHistory, sort) {
        TrackSorter.sections(sortedHistory, sort)
    }
    val localSections = remember(sortedLocal, sort) {
        TrackSorter.sections(sortedLocal, sort)
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
                    searchCategory == null || favoriteFolders[track.id] == searchCategory
                }
                LibrarySearchFilter.HISTORY -> history
                LibrarySearchFilter.LOCAL -> localTracks
            }
            filtered.filter(::matches).distinctBy { it.id }
        }
    }
    val sortedSearch = remember(searchResults, sort, ascending) {
        TrackSorter.sort(searchResults, sort, ascending)
    }
    val searchSections = remember(sortedSearch, sort) {
        TrackSorter.sections(sortedSearch, sort)
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

    LaunchedEffect(pagerState.currentPage) {
        selectionMode = false
        libraryHeaderVisible = true
        viewModel.clearSelection()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "音乐库",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(
                onClick = {
                    searchMode = !searchMode
                    if (!searchMode) searchQuery = ""
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
                categories = categories,
                selectedCategory = searchCategory,
                onCategoryChange = { searchCategory = it },
                results = sortedSearch,
                sections = searchSections,
                sort = sort,
                ascending = ascending,
                onSort = { sort = it },
                onToggleOrder = { ascending = !ascending },
                favoriteIds = favoriteIds,
                favoriteFolders = favoriteFolders,
                downloadedIds = downloadedIds,
                onPlayQueue = onPlayQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                modifier = Modifier.weight(1f),
            )
        } else {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.label) },
                    )
                }
            }

        AnimatedVisibility(
            visible = pagerState.currentPage != 0 || libraryHeaderVisible,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            SortBar(
                sort = sort,
                ascending = ascending,
                onSort = { sort = it },
                onToggleOrder = { ascending = !ascending },
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (LibraryTab.entries[page]) {
                LibraryTab.FAVORITES -> FavoritesTabContent(
                    favorites = sortedFavorites,
                    sections = favoriteSections,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    defaultCategory = defaultCategory,
                    favoriteFolders = favoriteFolders,
                    downloadedIds = downloadedIds,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    selectedTracks = sortedFavorites.filter { it.id in selectedIds },
                    onSelectCategory = { category ->
                        selectionMode = false
                        viewModel.clearSelection()
                        viewModel.setCategory(category)
                    },
                    onCreateCategory = { showNewCategory = true },
                    onPlayQueue = onPlayQueue,
                    onSelectionModeChange = {
                        selectionMode = it
                        if (!it) viewModel.clearSelection()
                    },
                    onToggleSelection = viewModel::toggleSelection,
                    onSelectAll = { viewModel.selectAll(sortedFavorites) },
                    onClearSelection = {
                        selectionMode = false
                        viewModel.clearSelection()
                    },
                    onAddFavorites = { tracks, category ->
                        viewModel.addFavorites(tracks, category)
                        selectionMode = false
                    },
                    onDownloadSelected = { tracks, quality ->
                        viewModel.downloadSelected(tracks, quality)
                        selectionMode = false
                    },
                    downloadQualities = viewModel::downloadQualities,
                    onHeaderVisibilityChange = { libraryHeaderVisible = it },
                    modifier = Modifier.fillMaxSize(),
                )

                LibraryTab.HISTORY -> HistoryTabContent(
                    history = sortedHistory,
                    sections = historySections,
                    categories = categories,
                    defaultCategory = defaultCategory,
                    favoriteFolders = favoriteFolders,
                    downloadedIds = downloadedIds,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onSelectionModeChange = {
                        selectionMode = it
                        if (!it) viewModel.clearSelection()
                    },
                    onToggleSelection = viewModel::toggleSelection,
                    onSelectAll = { viewModel.selectAll(sortedHistory) },
                    onClearSelection = {
                        selectionMode = false
                        viewModel.clearSelection()
                    },
                    onAddFavorites = { tracks, category ->
                        viewModel.addFavorites(tracks, category)
                        selectionMode = false
                    },
                    onDownloadSelected = { tracks, quality ->
                        viewModel.downloadSelected(tracks, quality)
                        selectionMode = false
                    },
                    downloadQualities = viewModel::downloadQualities,
                    onPlayQueue = onPlayQueue,
                    onClearHistory = viewModel::clearHistory,
                    modifier = Modifier.fillMaxSize(),
                )

                LibraryTab.LOCAL -> LocalTabContent(
                    tracks = sortedLocal,
                    sections = localSections,
                    favoriteIds = favoriteIds,
                    favoriteFolders = favoriteFolders,
                    downloadedIds = downloadedIds,
                    categories = categories,
                    defaultCategory = defaultCategory,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onSelectionModeChange = {
                        selectionMode = it
                        if (!it) viewModel.clearSelection()
                    },
                    onToggleSelection = viewModel::toggleSelection,
                    onSelectAll = { viewModel.selectAll(sortedLocal) },
                    onClearSelection = {
                        selectionMode = false
                        viewModel.clearSelection()
                    },
                    onAddFavorites = { tracks, category ->
                        viewModel.addFavorites(tracks, category)
                        selectionMode = false
                    },
                    onDownloadSelected = { tracks, quality ->
                        viewModel.downloadSelected(tracks, quality)
                        selectionMode = false
                    },
                    onDeleteSelected = {
                        viewModel.deleteLocalSelection(sortedLocal)
                        selectionMode = false
                    },
                    downloadQualities = viewModel::downloadQualities,
                    onPlayQueue = onPlayQueue,
                    onRequestImport = { showImportOptions = true },
                    onOpenDownloads = onOpenDownloads,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    favoriteFolders: Map<String, String>,
    downloadedIds: Set<String>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragLetter by remember { mutableStateOf<String?>(null) }
    val currentSection by remember(listState, sections) {
        derivedStateOf {
            sections.lastOrNull { it.itemIndex <= listState.firstVisibleItemIndex }?.label
        }
    }
    LaunchedEffect(query, filter, selectedCategory) {
        if (results.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
            ascending = ascending,
            onSort = onSort,
            onToggleOrder = onToggleOrder,
        )
        Text(
            text = if (query.isBlank()) "输入关键词开始搜索" else "找到 ${results.size} 首",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            else -> Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 30.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { onPlayQueue(results, index) },
                            isFavorite = track.id in favoriteIds,
                            onToggleFavorite = { onToggleFavorite(track) },
                            downloaded = track.id in downloadedIds,
                            categoryLabel = favoriteFolders[track.id],
                        )
                    }
                }
                AlphabetIndex(
                    sections = sections,
                    currentSection = currentSection,
                    onSelect = { section ->
                        scope.launch { listState.scrollToItem(section.itemIndex) }
                    },
                    onDragLetter = { dragLetter = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp),
                )
                AlphabetDragBubble(
                    letter = dragLetter,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun SortBar(
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "排序",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            TrackSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { onSort(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .height(28.dp)
                        .padding(end = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleOrder, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (ascending) {
                        Icons.Rounded.ArrowUpward
                    } else {
                        Icons.Rounded.ArrowDownward
                    },
                    contentDescription = if (ascending) "升序" else "降序",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTabContent(
    favorites: List<Track>,
    sections: List<AlphabetSection>,
    categories: List<String>,
    selectedCategory: String?,
    defaultCategory: String,
    favoriteFolders: Map<String, String>,
    downloadedIds: Set<String>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    selectedTracks: List<Track>,
    onSelectCategory: (String?) -> Unit,
    onCreateCategory: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, String) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onHeaderVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragLetter by remember { mutableStateOf<String?>(null) }
    val currentSection by remember(listState, sections) {
        derivedStateOf {
            sections.lastOrNull { it.itemIndex <= listState.firstVisibleItemIndex }?.label
        }
    }
    val validIds = remember(favorites) { favorites.map { it.id }.toSet() }
    var chosenCategory by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var chosenQuality by remember { mutableStateOf<Int?>(null) }
    var headerVisible by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = 0
        var previousOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > previousIndex ||
                    (index == previousIndex && offset > previousOffset)
                headerVisible = !scrollingDown || (index == 0 && offset == 0)
                previousIndex = index
                previousOffset = offset
            }
    }
    LaunchedEffect(headerVisible) {
        onHeaderVisibilityChange(headerVisible)
    }

    LaunchedEffect(selectedIds, favorites) {
        qualities = if (selectedIds.isEmpty()) emptyList() else downloadQualities(selectedTracks)
        if (chosenQuality !in qualities) chosenQuality = qualities.firstOrNull()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = headerVisible,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Column {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { onSelectCategory(null) },
                                    label = {
                                        Text("全部", style = MaterialTheme.typography.labelSmall)
                                    },
                                    modifier = Modifier.height(28.dp),
                                )
                            }
                            items(categories) { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { onSelectCategory(category) },
                                    label = {
                                        Text(
                                            category,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    modifier = Modifier.height(28.dp),
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = onCreateCategory,
                                    label = {
                                        Text("+ 新建", style = MaterialTheme.typography.labelSmall)
                                    },
                                    modifier = Modifier.height(28.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 30.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (selectionMode) {
                                    "已选 ${selectedIds.size} 首"
                                } else {
                                    "共 ${favorites.size} 首"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            if (!selectionMode) {
                                TextButton(
                                    enabled = favorites.isNotEmpty(),
                                    onClick = { onPlayQueue(favorites, 0) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) { Text("播放全部", style = MaterialTheme.typography.labelMedium) }
                                TextButton(
                                    onClick = { onSelectionModeChange(true) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) { Text("选择", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }

            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = if (selectedCategory == null) "还没有收藏歌曲" else "这个收藏夹是空的",
                        description = "在搜索结果或播放页收藏歌曲；默认收藏夹为“$defaultCategory”。",
                        icon = Icons.Rounded.FavoriteBorder,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .dragSelect(
                            enabled = true,
                            listState = listState,
                            validKeys = validIds,
                            onSelect = { id ->
                                if (!selectionMode) onSelectionModeChange(true)
                                onToggleSelection(id)
                            },
                        ),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 30.dp,
                        bottom = if (selectionMode) 100.dp else 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(favorites, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = {
                                if (selectionMode) onToggleSelection(track.id)
                                else onPlayQueue(favorites, index)
                            },
                            isFavorite = true,
                            showFavoriteState = false,
                            onToggleFavorite = null,
                            downloaded = track.id in downloadedIds,
                            categoryLabel = favoriteFolders[track.id],
                            selected = if (selectionMode) track.id in selectedIds else null,
                        )
                    }
                }
            }
        }

        AlphabetIndex(
            sections = sections,
            currentSection = currentSection,
            onSelect = { section -> scope.launch { listState.scrollToItem(section.itemIndex) } },
            onDragLetter = { dragLetter = it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 2.dp),
        )
        AlphabetDragBubble(
            letter = dragLetter,
            modifier = Modifier.align(Alignment.Center),
        )

        if (selectionMode) {
            TrackSelectionBar(
                count = selectedIds.size,
                categories = (categories + defaultCategory).distinct(),
                chosenCategory = chosenCategory,
                onCategoryChange = { chosenCategory = it },
                qualities = qualities,
                chosenQuality = chosenQuality,
                onQualityChange = { chosenQuality = it },
                onFavorite = { onAddFavorites(favorites, chosenCategory) },
                onDownload = { onDownloadSelected(favorites, chosenQuality) },
                onSelectAll = onSelectAll,
                onCancel = onClearSelection,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun TrackSelectionBar(
    count: Int,
    categories: List<String>,
    chosenCategory: String,
    onCategoryChange: (String) -> Unit,
    qualities: List<Int>,
    chosenQuality: Int?,
    onQualityChange: (Int) -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var categoryMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box {
                CompactTextButton(
                    text = "$chosenCategory ▾",
                    onClick = { categoryMenu = true },
                )
                DropdownMenu(
                    expanded = categoryMenu,
                    onDismissRequest = { categoryMenu = false },
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                categoryMenu = false
                                onCategoryChange(category)
                            },
                        )
                    }
                }
            }
            CompactTextButton(text = "收藏", onClick = onFavorite, enabled = count > 0)
            Spacer(Modifier.weight(1f))
            if (qualities.isNotEmpty()) {
                Box {
                    CompactTextButton(
                        text = "${chosenQuality?.let(::qualityShortLabel) ?: "标准"} ▾",
                        onClick = { qualityMenu = true },
                    )
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false },
                    ) {
                        qualities.forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(qualityLabel(quality)) },
                                onClick = {
                                    qualityMenu = false
                                    onQualityChange(quality)
                                },
                            )
                        }
                    }
                }
            }
            CompactTextButton(text = "下载", onClick = onDownload, enabled = count > 0)
            CompactTextButton(text = "全选", onClick = onSelectAll, enabled = count > 0)
            if (onDelete != null) {
                CompactTextButton(text = "删除", onClick = onDelete, enabled = count > 0)
            }
            CompactTextButton(text = "取消", onClick = onCancel)
        }
    }
}

@Composable
private fun CompactTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(text = text, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTabContent(
    history: List<Track>,
    sections: List<AlphabetSection>,
    categories: List<String>,
    defaultCategory: String,
    favoriteFolders: Map<String, String>,
    downloadedIds: Set<String>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, String) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragLetter by remember { mutableStateOf<String?>(null) }
    val currentSection by remember(listState, sections) {
        derivedStateOf {
            sections.lastOrNull { it.itemIndex <= listState.firstVisibleItemIndex }?.label
        }
    }
    val validIds = remember(history) { history.map { it.id }.toSet() }
    val selectedTracks = remember(history, selectedIds) {
        history.filter { it.id in selectedIds }
    }
    var chosenCategory by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var chosenQuality by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedIds, history) {
        qualities = if (selectedIds.isEmpty()) emptyList() else downloadQualities(selectedTracks)
        if (chosenQuality !in qualities) chosenQuality = qualities.firstOrNull()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectionMode) {
                            "已选 ${selectedIds.size} 首"
                        } else {
                            "最近播放 ${history.size} 首"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectionMode) {
                        TextButton(
                            onClick = onClearSelection,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("取消", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("清空记录", style = MaterialTheme.typography.labelMedium) }
                        if (history.isNotEmpty()) {
                            TextButton(
                                onClick = { onSelectionModeChange(true) },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("选择", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
            if (history.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "还没有播放记录",
                        description = "播放过的歌曲会自动记录在这里。",
                        icon = Icons.Rounded.History,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .dragSelect(
                            enabled = true,
                            listState = listState,
                            validKeys = validIds,
                            onSelect = { id ->
                                if (!selectionMode) onSelectionModeChange(true)
                                onToggleSelection(id)
                            },
                        ),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 30.dp,
                        bottom = if (selectionMode) 100.dp else 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(history, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = {
                                if (selectionMode) onToggleSelection(track.id)
                                else onPlayQueue(history, index)
                            },
                            isFavorite = track.id in favoriteFolders,
                            showFavoriteState = false,
                            onToggleFavorite = null,
                            downloaded = track.id in downloadedIds,
                            categoryLabel = favoriteFolders[track.id],
                            selected = if (selectionMode) track.id in selectedIds else null,
                        )
                    }
                }
            }
        }

        AlphabetIndex(
            sections = sections,
            currentSection = currentSection,
            onSelect = { section -> scope.launch { listState.scrollToItem(section.itemIndex) } },
            onDragLetter = { dragLetter = it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 2.dp),
        )
        AlphabetDragBubble(
            letter = dragLetter,
            modifier = Modifier.align(Alignment.Center),
        )

        if (selectionMode) {
            TrackSelectionBar(
                count = selectedIds.size,
                categories = (categories + defaultCategory).distinct(),
                chosenCategory = chosenCategory,
                onCategoryChange = { chosenCategory = it },
                qualities = qualities,
                chosenQuality = chosenQuality,
                onQualityChange = { chosenQuality = it },
                onFavorite = { onAddFavorites(history, chosenCategory) },
                onDownload = { onDownloadSelected(history, chosenQuality) },
                onSelectAll = onSelectAll,
                onCancel = onClearSelection,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalTabContent(
    tracks: List<Track>,
    sections: List<AlphabetSection>,
    favoriteIds: Set<String>,
    favoriteFolders: Map<String, String>,
    downloadedIds: Set<String>,
    categories: List<String>,
    defaultCategory: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, String) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    onDeleteSelected: () -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onRequestImport: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShowLocation: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragLetter by remember { mutableStateOf<String?>(null) }
    val currentSection by remember(listState, sections) {
        derivedStateOf {
            sections.lastOrNull { it.itemIndex <= listState.firstVisibleItemIndex }?.label
        }
    }
    val validIds = remember(tracks) { tracks.map { it.id }.toSet() }
    val selectedTracks = remember(tracks, selectedIds) {
        tracks.filter { it.id in selectedIds }
    }
    var chosenCategory by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var chosenQuality by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedIds, tracks) {
        qualities = if (selectedIds.isEmpty()) emptyList() else downloadQualities(selectedTracks)
        if (chosenQuality !in qualities) chosenQuality = qualities.firstOrNull()
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selectionMode) {
                        "已选 ${selectedIds.size} 首"
                    } else {
                        "本地与已下载 ${tracks.size} 首"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!selectionMode) {
                    TextButton(
                        onClick = onRequestImport,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("导入", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = onOpenDownloads,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("下载", style = MaterialTheme.typography.labelMedium) }
                    if (tracks.isNotEmpty()) {
                        TextButton(
                            onClick = { onSelectionModeChange(true) },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("选择", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "导入或下载音乐",
                    description = "点击导入可一键扫描媒体库，或手动选择文件/文件夹；已下载完成的歌曲会自动加入这里。",
                    icon = Icons.Rounded.LibraryMusic,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .dragSelect(
                        enabled = true,
                        listState = listState,
                        validKeys = validIds,
                        onSelect = { id ->
                            if (!selectionMode) onSelectionModeChange(true)
                            onToggleSelection(id)
                        },
                    ),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 30.dp,
                    bottom = if (selectionMode) 100.dp else 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = {
                            if (selectionMode) {
                                onToggleSelection(track.id)
                            } else {
                                onPlayQueue(tracks, index)
                            }
                        },
                        isFavorite = track.id in favoriteIds,
                        showFavoriteState = false,
                        onToggleFavorite = null,
                        downloaded = track.id in downloadedIds,
                        categoryLabel = favoriteFolders[track.id],
                        infoAction = if (selectionMode) null else ({ onShowLocation(track) }),
                        selected = if (selectionMode) track.id in selectedIds else null,
                    )
                }
            }
        }
    }
        AlphabetIndex(
            sections = sections,
            currentSection = currentSection,
            onSelect = { section -> scope.launch { listState.scrollToItem(section.itemIndex) } },
            onDragLetter = { dragLetter = it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 2.dp),
        )
        AlphabetDragBubble(
            letter = dragLetter,
            modifier = Modifier.align(Alignment.Center),
        )
        if (selectionMode) {
            TrackSelectionBar(
                count = selectedIds.size,
                categories = (categories + defaultCategory).distinct(),
                chosenCategory = chosenCategory,
                onCategoryChange = { chosenCategory = it },
                qualities = qualities,
                chosenQuality = chosenQuality,
                onQualityChange = { chosenQuality = it },
                onFavorite = { onAddFavorites(tracks, chosenCategory) },
                onDownload = { onDownloadSelected(tracks, chosenQuality) },
                onSelectAll = onSelectAll,
                onCancel = onClearSelection,
                onDelete = onDeleteSelected,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
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
