/**
 * 文件作用：两端共用的音乐库页面，包含收藏、最近播放、本地音乐、库内搜索与批量操作。
 */
package com.banlimusic.shared.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.sort.AlphabetSection
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiTrackListRow
import com.banlimusic.shared.ui.components.CompactTextButton
import com.banlimusic.shared.ui.components.ListTabLabel
import com.banlimusic.shared.ui.components.SelectionExtraAction
import com.banlimusic.shared.ui.components.SortBar
import com.banlimusic.shared.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.shared.ui.components.TrackListHeaderSpec
import com.banlimusic.shared.ui.components.TrackListPanel
import com.banlimusic.shared.ui.components.TrackScreenHeader
import com.banlimusic.shared.ui.components.TrackSelectionActionBar
import com.banlimusic.shared.ui.components.TrackSourceBadges
import com.banlimusic.shared.ui.components.formatBytes
import com.banlimusic.shared.ui.components.rememberSelectionActionsState
import com.banlimusic.shared.ui.components.rememberTrackSelectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Snapshot of everything the shared library layout renders. */
data class LibraryUiState(
    val favorites: List<Track> = emptyList(),
    val history: List<Track> = emptyList(),
    val importedTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val downloadedBytes: Long = 0L,
    val categories: List<String> = emptyList(),
    val defaultCategory: String = DEFAULT_FAVORITE_CATEGORY,
    /** Preselected quality for the batch download menu. */
    val downloadQualityKbps: Int = 128,
    val favoriteFolders: Map<String, Set<String>> = emptyMap(),
    val favoriteIds: Set<String> = emptySet(),
    val downloadedIds: Set<String> = emptySet(),
    val preferredSources: Map<String, String> = emptyMap(),
    /** False on platforms without a source picker; hides the tappable "+N" badge. */
    val supportsSourcePicker: Boolean = true,
    /** Desktop uses mouse drag selection instead of long-press drag selection. */
    val immediateDragSelection: Boolean = false,
)

/**
 * Platform bridge for the library. Layout and local UI state stay in this file;
 * adapters only provide data and persistence actions.
 */
interface LibraryActions {
    fun playQueue(tracks: List<Track>, index: Int)
    fun toggleFavorite(track: Track)
    fun addFavorites(tracks: List<Track>, folders: Set<String>)
    fun removeFavorites(tracks: List<Track>, folder: String?)
    fun download(tracks: List<Track>, qualityKbps: Int?)

    suspend fun downloadQualities(tracks: List<Track>): List<Int> = emptyList()
    fun createCategory(name: String) {}
    fun reorderCategories(categories: List<String>) {}
    fun deleteCategory(name: String) {}
    fun clearHistory() {}
    fun deleteHistory(tracks: List<Track>) {}
    fun deleteLocal(tracks: List<Track>) {}

    fun importScan() {}
    fun importFiles() {}
    fun importFolder() {}

    suspend fun localLocation(track: Track): String = "位置未知"
    fun openLocation(track: Track) {}
    fun onSourceClick(track: Track) {}
}

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

private fun List<AlphabetSection>.withListHeaderOffset(): List<AlphabetSection> =
    map { it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT) }

/**
 * Lets a platform adapter forward the system back button into the shared
 * screen's local UI state (selection → search → first tab).
 */
class LibraryScreenController {
    internal var backHandler: (() -> Boolean)? = null

    /** @return true when the shared screen consumed the back event. */
    fun handleBack(): Boolean = backHandler?.invoke() == true
}

/**
 * Shared library screen. Search mode, sort/filter, selection and dialogs are
 * local UI state, so both platforms automatically stay in sync.
 */
@Composable
fun BanLiLibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    artwork: @Composable (Track, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    controller: LibraryScreenController = remember { LibraryScreenController() },
) {
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(LibrarySearchFilter.ALL) }
    var searchCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { LibraryTab.entries.size })
    var favoriteSort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    var favoriteAscending by rememberSaveable { mutableStateOf(true) }
    var historySort by rememberSaveable { mutableStateOf(TrackSort.RECENT) }
    var historyAscending by rememberSaveable { mutableStateOf(true) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var localFilter by rememberSaveable { mutableStateOf(LocalFilter.ALL) }
    var localSort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    var localAscending by rememberSaveable { mutableStateOf(true) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var showImportOptions by rememberSaveable { mutableStateOf(false) }
    var manageCategories by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }
    var localInfoTrack by remember { mutableStateOf<Track?>(null) }
    var localInfoLocation by remember { mutableStateOf<String?>(null) }
    var localInfoLoading by remember { mutableStateOf(false) }
    val selection = rememberTrackSelectionState()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val folderCategories = remember(state.categories, state.defaultCategory) {
        (state.categories + state.defaultCategory).mergeCategoryLabels()
    }
    val visibleFavorites = remember(state.favorites, selectedCategory, state.favoriteFolders) {
        if (selectedCategory == null) {
            state.favorites
        } else {
            state.favorites.filter { selectedCategory in state.favoriteFolders[it.id].orEmpty() }
        }
    }
    val sortedFavorites = remember(visibleFavorites, favoriteSort, favoriteAscending) {
        TrackSorter.sort(visibleFavorites, favoriteSort, favoriteAscending)
    }
    val sortedHistory = remember(state.history, historySort, historyAscending) {
        TrackSorter.sort(state.history, historySort, historyAscending)
    }
    val allLocal = remember(state.importedTracks, state.downloadedTracks) {
        val merged = state.importedTracks + state.downloadedTracks
        merged.distinctBy { track ->
            track.sources.firstNotNullOfOrNull { it.localUri } ?: track.id
        }
    }
    val localTracks = remember(localFilter, state.importedTracks, state.downloadedTracks, allLocal) {
        when (localFilter) {
            LocalFilter.ALL -> allLocal
            LocalFilter.IMPORTED -> state.importedTracks
            LocalFilter.DOWNLOADED -> state.downloadedTracks
        }
    }
    val localSortOptions = if (localFilter == LocalFilter.DOWNLOADED) {
        DOWNLOADED_SORT_OPTIONS
    } else {
        IMPORTED_SORT_OPTIONS
    }
    val effectiveLocalSort = if (localSort in localSortOptions) localSort else localSortOptions.first()
    val sortedLocal = remember(localTracks, effectiveLocalSort, localAscending) {
        TrackSorter.sort(localTracks, effectiveLocalSort, localAscending)
    }

    val normalizedSearch = remember(searchQuery) { TextNormalizer.normalize(searchQuery) }
    val searchResults = remember(
        normalizedSearch,
        searchFilter,
        searchCategory,
        state.favorites,
        state.history,
        allLocal,
        state.favoriteFolders,
    ) {
        if (normalizedSearch.isBlank()) {
            emptyList()
        } else {
            fun matches(track: Track): Boolean = listOfNotNull(
                track.title,
                track.album?.title,
                track.artistLine,
            ).any { TextNormalizer.normalize(it).contains(normalizedSearch) }

            val filtered = when (searchFilter) {
                LibrarySearchFilter.ALL -> state.favorites + state.history + allLocal
                LibrarySearchFilter.FAVORITES -> state.favorites.filter { track ->
                    searchCategory == null ||
                        searchCategory in state.favoriteFolders[track.id].orEmpty()
                }
                LibrarySearchFilter.HISTORY -> state.history
                LibrarySearchFilter.LOCAL -> allLocal
            }
            filtered.filter(::matches).distinctBy { it.id }
        }
    }
    val sortedSearch = remember(searchResults, favoriteSort, favoriteAscending) {
        TrackSorter.sort(searchResults, favoriteSort, favoriteAscending)
    }

    fun exitSelection() = selection.exit()

    DisposableEffect(controller) {
        controller.backHandler = {
            when {
                selection.mode -> {
                    exitSelection()
                    true
                }
                searchMode -> {
                    searchMode = false
                    searchQuery = ""
                    keyboard?.hide()
                    true
                }
                pagerState.currentPage != 0 -> {
                    scope.launch { pagerState.animateScrollToPage(0) }
                    true
                }
                else -> false
            }
        }
        onDispose { controller.backHandler = null }
    }

    LaunchedEffect(pagerState.currentPage) { exitSelection() }
    LaunchedEffect(localFilter) { exitSelection() }

    Column(modifier = modifier.fillMaxSize()) {
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
                sort = favoriteSort,
                ascending = favoriteAscending,
                onSort = { favoriteSort = it },
                onToggleOrder = { favoriteAscending = !favoriteAscending },
                state = state,
                actions = actions,
                artwork = artwork,
                selection = selection,
                defaultCategory = state.defaultCategory,
                modifier = Modifier.weight(1f),
            )
        } else {
            val tab = pagerState.currentPage
            PrimaryTabRow(selectedTabIndex = tab) {
                LibraryTab.entries.forEachIndexed { index, entry ->
                    Tab(
                        selected = tab == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { ListTabLabel(entry.label) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (LibraryTab.entries[page]) {
                    LibraryTab.FAVORITES -> FavoritesTabContent(
                        tracks = sortedFavorites,
                        categories = folderCategories,
                        selectedCategory = selectedCategory,
                        defaultCategory = state.defaultCategory,
                        state = state,
                        actions = actions,
                        artwork = artwork,
                        selection = selection,
                        sort = favoriteSort,
                        ascending = favoriteAscending,
                        onSort = { favoriteSort = it },
                        onToggleOrder = { favoriteAscending = !favoriteAscending },
                        onSelectCategory = {
                            exitSelection()
                            selectedCategory = it
                        },
                        onCreateCategory = { showNewCategory = true },
                        onManageCategories = { manageCategories = true },
                        modifier = Modifier.fillMaxSize(),
                    )

                    LibraryTab.HISTORY -> HistoryTabContent(
                        tracks = sortedHistory,
                        categories = folderCategories,
                        defaultCategory = state.defaultCategory,
                        state = state,
                        actions = actions,
                        artwork = artwork,
                        selection = selection,
                        sort = historySort,
                        ascending = historyAscending,
                        onSort = { historySort = it },
                        onToggleOrder = { historyAscending = !historyAscending },
                        modifier = Modifier.fillMaxSize(),
                    )

                    LibraryTab.LOCAL -> LocalTabContent(
                        tracks = sortedLocal,
                        importedCount = state.importedTracks.size,
                        downloadedCount = state.downloadedTracks.size,
                        downloadedBytes = state.downloadedBytes,
                        filter = localFilter,
                        sort = effectiveLocalSort,
                        sortOptions = localSortOptions,
                        ascending = localAscending,
                        categories = folderCategories,
                        defaultCategory = state.defaultCategory,
                        state = state,
                        actions = actions,
                        artwork = artwork,
                        selection = selection,
                        onFilterChange = { localFilter = it },
                        onSort = { localSort = it },
                        onToggleOrder = { localAscending = !localAscending },
                        onRequestImport = { showImportOptions = true },
                        onShowLocation = { track ->
                            localInfoTrack = track
                            localInfoLocation = null
                            localInfoLoading = true
                            scope.launch {
                                localInfoLocation = try {
                                    actions.localLocation(track)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Throwable) {
                                    "位置未知"
                                } finally {
                                    localInfoLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
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
                        actions.createCategory(name)
                        selectedCategory = name
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

    if (showImportOptions) {
        AlertDialog(
            onDismissRequest = { showImportOptions = false },
            title = { Text("导入本地音乐") },
            text = {
                Column {
                    Text("自动识别会扫描系统媒体库或本地音乐目录，并过滤时长过短的提示音与铃声。")
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showImportOptions = false
                            actions.importScan()
                        },
                    ) { Text("一键导入（自动识别歌曲）") }
                    TextButton(
                        onClick = {
                            showImportOptions = false
                            actions.importFiles()
                        },
                    ) { Text("选择文件导入") }
                    TextButton(
                        onClick = {
                            showImportOptions = false
                            actions.importFolder()
                        },
                    ) { Text("选择文件夹导入") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportOptions = false }) { Text("取消") }
            },
        )
    }

    if (manageCategories) {
        CategoryManagerDialog(
            categories = state.categories,
            protectedNames = setOf(DEFAULT_FAVORITE_CATEGORY, state.defaultCategory),
            onReorder = actions::reorderCategories,
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
                        actions.deleteCategory(category)
                        pendingDeleteCategory = null
                        if (selectedCategory == category) selectedCategory = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) { Text("取消") }
            },
        )
    }

    localInfoTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { localInfoTrack = null },
            title = { Text(track.title) },
            text = {
                if (localInfoLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在读取文件位置…")
                    }
                } else {
                    Text(localInfoLocation ?: "位置未知")
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            localInfoTrack = null
                            actions.openLocation(track)
                        },
                    ) { Text("打开目录") }
                    TextButton(onClick = { localInfoTrack = null }) { Text("关闭") }
                }
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
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    state: LibraryUiState,
    actions: LibraryActions,
    artwork: @Composable (Track, Modifier) -> Unit,
    selection: com.banlimusic.shared.ui.components.TrackSelectionState,
    defaultCategory: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val selectionActions = rememberSelectionActionsState(
        selectionMode = selection.mode,
        selectedIds = selection.selectedIds,
        tracks = results,
        defaultCategory = defaultCategory,
        defaultQualityKbps = state.downloadQualityKbps,
        downloadQualities = actions::downloadQualities,
    )
    val selectedTracks = results.filter { it.id in selection.selectedIds }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        withFrameNanos { }
        keyboard?.show()
    }
    LaunchedEffect(query, filter, selectedCategory) {
        if (selection.mode) selection.exit()
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
            query.isBlank() -> BanLiEmptyState(
                title = "搜索音乐库",
                description = "可搜索歌名、专辑名和歌手名，并按收藏、最近播放或本地/下载筛选。",
                icon = Icons.Rounded.Search,
            )

            results.isEmpty() -> BanLiEmptyState(
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
                    selectionMode = selection.mode,
                    selectedCount = selection.selectedIds.size,
                ),
                listState = listState,
                sections = remember(results, sort) {
                    TrackSorter.sections(results, sort).withListHeaderOffset()
                },
                ascending = ascending,
                selectionMode = selection.mode,
                onStartSelection = {
                    keyboard?.hide()
                    selection.enter()
                },
                onSelect = selection::toggle,
                onSelectAll = { selection.selectAll(results.map { it.id }) },
                onCancelSelection = selection::exit,
                onSort = {},
                onToggleOrder = {},
                emptyContent = {
                    BanLiEmptyState(
                        title = "没有找到相关歌曲",
                        description = "试试更短的关键词，或切换到其他筛选条件。",
                        icon = Icons.Rounded.Search,
                    )
                },
                modifier = Modifier.weight(1f),
                immediateDragSelection = state.immediateDragSelection,
                countActions = {
                    CompactTextButton(
                        text = "选择",
                        onClick = {
                            keyboard?.hide()
                            selection.enter()
                        },
                    )
                },
                selectionBar = {
                    TrackSelectionActionBar(
                        categories = (categories + defaultCategory).mergeCategoryLabels(),
                        state = selectionActions,
                        onFavorite = {
                            actions.addFavorites(selectedTracks, selectionActions.chosenCategories)
                            selection.exit()
                        },
                        onDownload = {
                            actions.download(selectedTracks, selectionActions.chosenQuality)
                            selection.exit()
                        },
                        favoriteEnabled = selectedTracks.isNotEmpty() &&
                            selectionActions.chosenCategories.isNotEmpty(),
                        downloadEnabled = selectedTracks.isNotEmpty() &&
                            selectionActions.qualities.isNotEmpty(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                },
            ) { index, track ->
                LibraryRow(
                    track = track,
                    artwork = artwork,
                    onClick = {
                        if (selection.mode) selection.toggle(track.id)
                        else actions.playQueue(results, index)
                    },
                            isFavorite = track.id in state.favoriteIds,
                    onToggleFavorite = if (selection.mode) null else {
                        { actions.toggleFavorite(track) }
                    },
                    downloaded = track.id in state.downloadedIds,
                    categoryLabel = state.favoriteFolders[track.id]?.joinToString(" · "),
                    preferredProviderId = state.preferredSources[track.id],
                    onSourceClick = if (state.supportsSourcePicker) {
                        { actions.onSourceClick(track) }
                    } else {
                        null
                    },
                    selected = if (selection.mode) track.id in selection.selectedIds else null,
                )
            }
        }
    }
}

@Composable
private fun FavoritesTabContent(
    tracks: List<Track>,
    categories: List<String>,
    selectedCategory: String?,
    defaultCategory: String,
    state: LibraryUiState,
    actions: LibraryActions,
    artwork: @Composable (Track, Modifier) -> Unit,
    selection: com.banlimusic.shared.ui.components.TrackSelectionState,
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onCreateCategory: () -> Unit,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectionActions = rememberSelectionActionsState(
        selectionMode = selection.mode,
        selectedIds = selection.selectedIds,
        tracks = tracks,
        defaultCategory = defaultCategory,
        defaultQualityKbps = state.downloadQualityKbps,
        downloadQualities = actions::downloadQualities,
    )
    val selectedTracks = tracks.filter { it.id in selection.selectedIds }

    TrackListPanel(
        spec = TrackListHeaderSpec(
            countText = "共 ${tracks.size} 首",
            selectionMode = selection.mode,
            selectedCount = selection.selectedIds.size,
            sort = sort,
            sortOptions = FAVORITE_SORT_OPTIONS,
            ascending = ascending,
            categories = categories,
            selectedCategory = selectedCategory,
        ),
        listState = listState,
        items = tracks,
        itemKey = { it.id },
        validKey = { it.id },
        sections = remember(tracks, sort) {
            TrackSorter.sections(tracks, sort).withListHeaderOffset()
        },
        ascending = ascending,
        selectionMode = selection.mode,
        onStartSelection = selection::enter,
        onSelect = selection::toggle,
        onSelectAll = { selection.selectAll(tracks.map { it.id }) },
        onCancelSelection = selection::exit,
        onSort = onSort,
        onToggleOrder = onToggleOrder,
        onCategory = onSelectCategory,
        countActions = {
            if (tracks.isNotEmpty()) {
                CompactTextButton(
                    text = "播放全部",
                    onClick = { actions.playQueue(tracks, 0) },
                )
            }
            CompactTextButton(text = "选择", onClick = selection::enter)
        },
        categoryTrailing = {
            CompactTextButton(text = "+ 新建", onClick = onCreateCategory)
            CompactTextButton(text = "管理", onClick = onManageCategories)
        },
        immediateDragSelection = state.immediateDragSelection,
        emptyContent = {
            BanLiEmptyState(
                title = if (selectedCategory == null) "还没有收藏歌曲" else "这个收藏夹是空的",
                description = "在搜索结果或播放页收藏歌曲；默认收藏夹为“$defaultCategory”。",
                icon = Icons.Rounded.FavoriteBorder,
            )
        },
        modifier = modifier,
        selectionBar = {
            TrackSelectionActionBar(
                categories = (categories + defaultCategory).mergeCategoryLabels(),
                state = selectionActions,
                onFavorite = {
                    actions.addFavorites(selectedTracks, selectionActions.chosenCategories)
                    selection.exit()
                },
                onDownload = {
                    actions.download(selectedTracks, selectionActions.chosenQuality)
                    selection.exit()
                },
                favoriteEnabled = selectedTracks.isNotEmpty() &&
                    selectionActions.chosenCategories.isNotEmpty(),
                downloadEnabled = selectedTracks.isNotEmpty() &&
                    selectionActions.qualities.isNotEmpty(),
                extraAction = SelectionExtraAction(
                    icon = Icons.Rounded.HeartBroken,
                    label = "取消收藏",
                    enabled = selectedTracks.isNotEmpty(),
                    onClick = {
                        actions.removeFavorites(selectedTracks, selectedCategory)
                        selection.exit()
                    },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) { index, track ->
        LibraryRow(
            track = track,
            artwork = artwork,
            onClick = {
                if (selection.mode) selection.toggle(track.id)
                else actions.playQueue(tracks, index)
            },
            isFavorite = true,
            showFavoriteState = !selection.mode,
            onToggleFavorite = null,
            downloaded = track.id in state.downloadedIds,
            categoryLabel = state.favoriteFolders[track.id]?.joinToString(" · "),
            preferredProviderId = state.preferredSources[track.id],
            onSourceClick = if (state.supportsSourcePicker) {
                { actions.onSourceClick(track) }
            } else {
                null
            },
            selected = if (selection.mode) track.id in selection.selectedIds else null,
        )
    }
}

@Composable
private fun HistoryTabContent(
    tracks: List<Track>,
    categories: List<String>,
    defaultCategory: String,
    state: LibraryUiState,
    actions: LibraryActions,
    artwork: @Composable (Track, Modifier) -> Unit,
    selection: com.banlimusic.shared.ui.components.TrackSelectionState,
    sort: TrackSort,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectionActions = rememberSelectionActionsState(
        selectionMode = selection.mode,
        selectedIds = selection.selectedIds,
        tracks = tracks,
        defaultCategory = defaultCategory,
        defaultQualityKbps = state.downloadQualityKbps,
        downloadQualities = actions::downloadQualities,
    )
    val selectedTracks = tracks.filter { it.id in selection.selectedIds }

    TrackListPanel(
        spec = TrackListHeaderSpec(
            countText = "最近播放 ${tracks.size} 首",
            selectionMode = selection.mode,
            selectedCount = selection.selectedIds.size,
            sort = sort,
            sortOptions = HISTORY_SORT_OPTIONS,
            ascending = ascending,
            categories = null,
        ),
        listState = listState,
        items = tracks,
        itemKey = { it.id },
        validKey = { it.id },
        sections = remember(tracks, sort) {
            TrackSorter.sections(tracks, sort).withListHeaderOffset()
        },
        ascending = ascending,
        selectionMode = selection.mode,
        onStartSelection = selection::enter,
        onSelect = selection::toggle,
        onSelectAll = { selection.selectAll(tracks.map { it.id }) },
        onCancelSelection = selection::exit,
        onSort = onSort,
        onToggleOrder = onToggleOrder,
        countActions = {
            CompactTextButton(text = "清空记录", onClick = actions::clearHistory)
            if (tracks.isNotEmpty()) {
                CompactTextButton(text = "选择", onClick = selection::enter)
            }
        },
        emptyContent = {
            BanLiEmptyState(
                title = "还没有播放记录",
                description = "播放过的歌曲会自动记录在这里。",
                icon = Icons.Rounded.History,
            )
        },
        modifier = modifier,
        immediateDragSelection = state.immediateDragSelection,
        selectionBar = {
            TrackSelectionActionBar(
                categories = (categories + defaultCategory).mergeCategoryLabels(),
                state = selectionActions,
                onFavorite = {
                    actions.addFavorites(selectedTracks, selectionActions.chosenCategories)
                    selection.exit()
                },
                onDownload = {
                    actions.download(selectedTracks, selectionActions.chosenQuality)
                    selection.exit()
                },
                favoriteEnabled = selectedTracks.isNotEmpty() &&
                    selectionActions.chosenCategories.isNotEmpty(),
                downloadEnabled = selectedTracks.isNotEmpty() &&
                    selectionActions.qualities.isNotEmpty(),
                extraAction = SelectionExtraAction(
                    icon = Icons.Rounded.Delete,
                    label = "删除",
                    enabled = selectedTracks.isNotEmpty(),
                    onClick = {
                        actions.deleteHistory(selectedTracks)
                        selection.exit()
                    },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) { index, track ->
        LibraryRow(
            track = track,
            artwork = artwork,
            onClick = {
                if (selection.mode) selection.toggle(track.id)
                else actions.playQueue(tracks, index)
            },
            isFavorite = track.id in state.favoriteFolders,
            showFavoriteState = false,
            onToggleFavorite = if (selection.mode) null else {
                { actions.toggleFavorite(track) }
            },
            downloaded = track.id in state.downloadedIds,
            categoryLabel = state.favoriteFolders[track.id]?.joinToString(" · "),
            preferredProviderId = state.preferredSources[track.id],
            onSourceClick = if (state.supportsSourcePicker) {
                { actions.onSourceClick(track) }
            } else {
                null
            },
            selected = if (selection.mode) track.id in selection.selectedIds else null,
        )
    }
}

@Composable
private fun LocalTabContent(
    tracks: List<Track>,
    importedCount: Int,
    downloadedCount: Int,
    downloadedBytes: Long,
    filter: LocalFilter,
    sort: TrackSort,
    sortOptions: List<TrackSort>,
    ascending: Boolean,
    categories: List<String>,
    defaultCategory: String,
    state: LibraryUiState,
    actions: LibraryActions,
    artwork: @Composable (Track, Modifier) -> Unit,
    selection: com.banlimusic.shared.ui.components.TrackSelectionState,
    onFilterChange: (LocalFilter) -> Unit,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    onRequestImport: () -> Unit,
    onShowLocation: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var pendingLocalDelete by remember { mutableStateOf<List<Track>?>(null) }
    val selectionActions = rememberSelectionActionsState(
        selectionMode = selection.mode,
        selectedIds = selection.selectedIds,
        tracks = tracks,
        defaultCategory = defaultCategory,
        defaultQualityKbps = state.downloadQualityKbps,
        downloadQualities = actions::downloadQualities,
    )
    val selectedTracks = tracks.filter { it.id in selection.selectedIds }
    val countText = when (filter) {
        LocalFilter.ALL -> "本地 $importedCount 首 · 已下载 $downloadedCount 首"
        LocalFilter.IMPORTED -> "本地 $importedCount 首"
        LocalFilter.DOWNLOADED -> "已下载 $downloadedCount 首 · 共 ${formatBytes(downloadedBytes)}"
    }

    TrackListPanel(
        spec = TrackListHeaderSpec(
            countText = countText,
            selectionMode = selection.mode,
            selectedCount = selection.selectedIds.size,
            sort = sort,
            sortOptions = sortOptions,
            ascending = ascending,
            categories = listOf(LocalFilter.IMPORTED.label, LocalFilter.DOWNLOADED.label),
            selectedCategory = when (filter) {
                LocalFilter.ALL -> null
                else -> filter.label
            },
        ),
        listState = listState,
        items = tracks,
        itemKey = { it.id },
        validKey = { it.id },
        sections = remember(tracks, sort) {
            TrackSorter.sections(tracks, sort).withListHeaderOffset()
        },
        ascending = ascending,
        selectionMode = selection.mode,
        onStartSelection = selection::enter,
        onSelect = selection::toggle,
        onSelectAll = { selection.selectAll(tracks.map { it.id }) },
        onCancelSelection = selection::exit,
        onSort = onSort,
        onToggleOrder = onToggleOrder,
        onCategory = { category ->
            onFilterChange(
                when (category) {
                    LocalFilter.IMPORTED.label -> LocalFilter.IMPORTED
                    LocalFilter.DOWNLOADED.label -> LocalFilter.DOWNLOADED
                    else -> LocalFilter.ALL
                },
            )
        },
        countActions = {
            CompactTextButton(text = "导入", onClick = onRequestImport)
            if (tracks.isNotEmpty()) {
                CompactTextButton(text = "选择", onClick = selection::enter)
            }
        },
        emptyContent = {
            BanLiEmptyState(
                title = "导入或下载音乐",
                description = "点击导入可一键扫描媒体库，或手动选择文件/文件夹；已下载完成的歌曲会自动加入这里。",
                icon = Icons.Rounded.LibraryMusic,
            )
        },
        modifier = modifier,
        immediateDragSelection = state.immediateDragSelection,
        selectionBar = {
            TrackSelectionActionBar(
                categories = (categories + defaultCategory).mergeCategoryLabels(),
                state = selectionActions,
                onFavorite = {
                    actions.addFavorites(selectedTracks, selectionActions.chosenCategories)
                    selection.exit()
                },
                onDownload = {
                    actions.download(selectedTracks, selectionActions.chosenQuality)
                    selection.exit()
                },
                favoriteEnabled = selectedTracks.isNotEmpty() &&
                    selectionActions.chosenCategories.isNotEmpty(),
                downloadEnabled = selectedTracks.isNotEmpty() &&
                    selectionActions.qualities.isNotEmpty(),
                extraAction = SelectionExtraAction(
                    icon = Icons.Rounded.Delete,
                    label = "删除",
                    enabled = selectedTracks.isNotEmpty(),
                    onClick = {
                        val hasDownloaded = selectedTracks.any { track ->
                            track.sources.any { it.providerId == "download" }
                        }
                        if (hasDownloaded) {
                            pendingLocalDelete = selectedTracks
                        } else {
                            actions.deleteLocal(selectedTracks)
                            selection.exit()
                        }
                    },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    ) { index, track ->
        LibraryRow(
            track = track,
            artwork = artwork,
            onClick = {
                if (selection.mode) selection.toggle(track.id)
                else actions.playQueue(tracks, index)
            },
            isFavorite = track.id in state.favoriteIds,
            showFavoriteState = false,
            onToggleFavorite = null,
            downloaded = track.id in state.downloadedIds,
            showDownloadBadge = false,
            categoryLabel = state.favoriteFolders[track.id]?.joinToString(" · "),
            preferredProviderId = state.preferredSources[track.id],
            onSourceClick = if (state.supportsSourcePicker) {
                { actions.onSourceClick(track) }
            } else {
                null
            },
            infoAction = if (selection.mode) null else ({ onShowLocation(track) }),
            selected = if (selection.mode) track.id in selection.selectedIds else null,
        )
    }

    pendingLocalDelete?.let { pending ->
        val imported = pending.filter { track ->
            track.sources.any { it.providerId == "local" }
        }
        val downloaded = pending.filter { track ->
            track.sources.any { it.providerId == "download" }
        }
        AlertDialog(
            onDismissRequest = { pendingLocalDelete = null },
            title = {
                Text(if (imported.isEmpty()) "已下载歌曲不能在这里删除" else "删除所选歌曲")
            },
            text = {
                Text(
                    buildString {
                        if (imported.isNotEmpty()) {
                            append("将移除 ${imported.size} 首本地导入记录（不会删除原始文件）。")
                        }
                        if (downloaded.isNotEmpty()) {
                            if (isNotEmpty()) append("\n\n")
                            append("${downloaded.size} 首已下载歌曲不会在这里删除，")
                            append("请到“下载 → 已下载”页面删除。")
                        }
                    },
                )
            },
            confirmButton = {
                if (imported.isEmpty()) {
                    TextButton(onClick = { pendingLocalDelete = null }) { Text("知道了") }
                } else {
                    TextButton(
                        onClick = {
                            actions.deleteLocal(imported)
                            pendingLocalDelete = null
                            selection.exit()
                        },
                    ) { Text("删除导入记录") }
                }
            },
            dismissButton = {
                if (imported.isNotEmpty()) {
                    TextButton(onClick = { pendingLocalDelete = null }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun LibraryRow(
    track: Track,
    artwork: @Composable (Track, Modifier) -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    downloaded: Boolean,
    categoryLabel: String?,
    preferredProviderId: String?,
    onSourceClick: (() -> Unit)?,
    selected: Boolean?,
    showFavoriteState: Boolean = false,
    showDownloadBadge: Boolean = true,
    infoAction: (() -> Unit)? = null,
) {
    BanLiTrackListRow(
        track = track,
        artwork = { artwork(track, it) },
        onClick = onClick,
        onLongClick = onLongClick,
        isFavorite = isFavorite,
        showFavoriteState = showFavoriteState,
        onToggleFavorite = onToggleFavorite,
        downloaded = downloaded,
        showDownloadBadge = showDownloadBadge,
        categoryLabel = categoryLabel,
        preferredProviderId = preferredProviderId,
        onSourceClick = onSourceClick,
        infoAction = infoAction,
        selected = selected,
    )
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
                                                        if (index == -1) {
                                                            return@detectDragGesturesAfterLongPress
                                                        }
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

/** Keeps the default folder visible once and preserves order. */
private fun List<String>.mergeCategoryLabels(): List<String> =
    map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun localFileDirectory(track: Track): String? = track.sources
    .firstNotNullOfOrNull { it.localUri }
    ?.let { uri -> uri.substringBeforeLast('/', "").takeIf { it.isNotBlank() } }
