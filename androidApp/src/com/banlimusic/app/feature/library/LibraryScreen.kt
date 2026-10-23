package com.banlimusic.app.feature.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.app.ui.components.dragSelect
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.ui.components.qualityShortLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class LibrarySort(val label: String) {
    TITLE("名称"),
    ARTIST("歌手"),
    ALBUM("专辑"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var libraryHeaderVisible by remember { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.TITLE) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var localInfoTrack by remember { mutableStateOf<Track?>(null) }
    var localInfoLocation by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sortedFavorites = remember(favorites, sort, ascending) {
        sortTracks(favorites, sort, ascending)
    }
    val sortedHistory = remember(history, sort, ascending) {
        sortTracks(history, sort, ascending)
    }
    val sortedLocal = remember(localTracks, sort, ascending) {
        sortTracks(localTracks, sort, ascending)
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.importLocalMusic()
        } else {
            Toast.makeText(context, "需要媒体读取权限才能导入本地音乐", Toast.LENGTH_SHORT).show()
        }
    }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = runCatching { viewModel.exportFavoritesJson() }.getOrNull()
                val ok = raw != null && withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(raw.toByteArray())
                        } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) "收藏已导出" else "导出失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            it.readText()
                        }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    Toast.makeText(context, "无法读取收藏文件", Toast.LENGTH_SHORT).show()
                } else {
                    pendingImportJson = raw
                }
            }
        }
    }

    fun requestLocalImport() {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.importLocalMusic() else importLauncher.launch(permission)
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "音乐库",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            listOf("收藏", "最近播放", "本地").forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        selectionMode = false
                        libraryHeaderVisible = true
                        viewModel.clearSelection()
                    },
                    text = { Text(label) },
                )
            }
        }

        AnimatedVisibility(
            visible = selectedTab != 0 || libraryHeaderVisible,
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

        when (selectedTab) {
            0 -> FavoritesTabContent(
                favorites = sortedFavorites,
                categories = categories,
                selectedCategory = selectedCategory,
                defaultCategory = defaultCategory,
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
                onRemoveFavorite = viewModel::removeFavorite,
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
                onExport = { exportJsonLauncher.launch("banlimusic-favorites.json") },
                onImport = {
                    importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                onHeaderVisibilityChange = { libraryHeaderVisible = it },
                modifier = Modifier.weight(1f),
            )

            1 -> HistoryTabContent(
                history = sortedHistory,
                onPlayQueue = onPlayQueue,
                onClearHistory = viewModel::clearHistory,
                modifier = Modifier.weight(1f),
            )

            else -> LocalTabContent(
                tracks = sortedLocal,
                onPlayQueue = onPlayQueue,
                onImport = ::requestLocalImport,
                onOpenDownloads = onOpenDownloads,
                onClearImported = viewModel::clearLocalMusic,
                onShowLocation = { track ->
                    scope.launch {
                        localInfoLocation = withContext(Dispatchers.IO) {
                            resolveLocalLocation(context, track)
                        }
                        localInfoTrack = track
                    }
                },
                modifier = Modifier.weight(1f),
            )
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

    pendingImportJson?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("导入收藏") },
            text = {
                Column {
                    Text("选择“覆盖导入”会清空当前收藏；选择“仅追加”会保留现有收藏。")
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            viewModel.importFavoritesJson(raw, replace = true)
                            pendingImportJson = null
                        },
                    ) { Text("覆盖导入") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importFavoritesJson(raw, replace = false)
                        pendingImportJson = null
                    },
                ) { Text("仅追加") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text("取消") }
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

@Composable
private fun SortBar(
    sort: LibrarySort,
    ascending: Boolean,
    onSort: (LibrarySort) -> Unit,
    onToggleOrder: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "排序",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        LibrarySort.entries.forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onSort(option) },
                label = { Text(option.label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleOrder) {
            Icon(
                imageVector = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = if (ascending) "升序" else "降序",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoritesTabContent(
    favorites: List<Track>,
    categories: List<String>,
    selectedCategory: String?,
    defaultCategory: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    selectedTracks: List<Track>,
    onSelectCategory: (String?) -> Unit,
    onCreateCategory: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onRemoveFavorite: (Track) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddFavorites: (List<Track>, String) -> Unit,
    onDownloadSelected: (List<Track>, Int?) -> Unit,
    downloadQualities: suspend (List<Track>) -> List<Int>,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onHeaderVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
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
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { onSelectCategory(null) },
                                label = { Text("全部") },
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onSelectCategory(category) },
                                label = { Text(category) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = onCreateCategory,
                                label = { Text("+ 新建") },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
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
                            ) { Text("播放全部") }
                            TextButton(onClick = { onSelectionModeChange(true) }) {
                                Text("选择")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(onClick = onExport, label = { Text("导出") })
                        AssistChip(onClick = onImport, label = { Text("导入") })
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
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
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
                            onToggleFavorite = if (selectionMode) {
                                null
                            } else {
                                { onRemoveFavorite(track) }
                            },
                            selected = if (selectionMode) track.id in selectedIds else null,
                            onLongClick = if (selectionMode) {
                                null
                            } else {
                                {
                                    onSelectionModeChange(true)
                                    onToggleSelection(track.id)
                                }
                            },
                            selectionDragModifier = if (selectionMode) {
                                Modifier.dragSelect(
                                    enabled = true,
                                    listState = listState,
                                    validKeys = validIds,
                                    onSelect = onToggleSelection,
                                )
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }

        if (selectionMode) {
            FavoritesSelectionBar(
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
private fun FavoritesSelectionBar(
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

@Composable
private fun HistoryTabContent(
    history: List<Track>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近播放 ${history.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearHistory) { Text("清空记录") }
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
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(history, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlayQueue(history, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalTabContent(
    tracks: List<Track>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onImport: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearImported: () -> Unit,
    onShowLocation: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "本地与已下载 ${tracks.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onImport) { Text("导入") }
            TextButton(onClick = onOpenDownloads) { Text("下载") }
            TextButton(onClick = onClearImported) { Text("清空导入") }
        }
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "导入或下载音乐",
                    description = "扫描系统媒体库中的音频；已下载完成的歌曲会自动加入这里。长按可查看位置。",
                    icon = Icons.Rounded.LibraryMusic,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlayQueue(tracks, index) },
                        onLongClick = { onShowLocation(track) },
                    )
                }
            }
        }
    }
}

private fun sortTracks(
    tracks: List<Track>,
    sort: LibrarySort,
    ascending: Boolean,
): List<Track> {
    val comparator = when (sort) {
        LibrarySort.TITLE -> compareBy<Track> { it.title.lowercase() }
        LibrarySort.ARTIST -> compareBy<Track> { it.artistLine.lowercase() }
        LibrarySort.ALBUM -> compareBy<Track> { it.album?.title.orEmpty().lowercase() }
    }.thenBy { it.title.lowercase() }
    return if (ascending) tracks.sortedWith(comparator) else tracks.sortedWith(comparator.reversed())
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
