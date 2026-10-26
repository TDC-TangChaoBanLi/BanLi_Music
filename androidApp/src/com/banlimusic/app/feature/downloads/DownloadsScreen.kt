/**
 * 文件作用：下载页界面，展示已下载歌曲列表与下载队列。
 */
package com.banlimusic.app.feature.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.domain.download.DownloadStatus
import com.banlimusic.app.domain.download.DownloadStatusLabels
import com.banlimusic.app.domain.download.DownloadTask
import com.banlimusic.app.domain.sort.AlphabetSection
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.CompactTextButton
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.ListTabLabel
import com.banlimusic.app.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.app.ui.components.TrackListHeaderSpec
import com.banlimusic.app.ui.components.TrackListPanel
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.app.ui.components.TrackScreenHeader
import com.banlimusic.app.ui.components.formatBytes
import com.banlimusic.app.ui.components.qualityTierLabel
import kotlinx.coroutines.launch

private val DOWNLOAD_SORT_OPTIONS = listOf(
    TrackSort.CREATED,
    TrackSort.TITLE,
    TrackSort.ARTIST,
)

@Composable
fun DownloadsScreen(
    onOpenPlayer: () -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(TrackSort.CREATED) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val active = tasks.filter { it.status != DownloadStatus.COMPLETED }
    val normalizedQuery = remember(searchQuery) { TextNormalizer.normalize(searchQuery) }
    val visibleCompleted = remember(completed, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            completed
        } else {
            completed.filter { task ->
                listOfNotNull(
                    task.track.title,
                    task.track.album?.title,
                    task.track.artistLine,
                ).any { TextNormalizer.normalize(it).contains(normalizedQuery) }
            }
        }
    }
    val visibleBytes = remember(visibleCompleted) { visibleCompleted.sumOf { it.totalBytes } }
    val sortedCompleted = remember(visibleCompleted, sort, ascending) {
        val order = TrackSorter.sort(visibleCompleted.map { it.track }, sort, ascending)
            .map { it.id }
            .withIndex()
            .associate { it.value to it.index }
        visibleCompleted.sortedWith(
            compareBy({ order[it.track.id] ?: Int.MAX_VALUE }, { it.createdAt }),
        )
    }
    val sections = remember(sortedCompleted, sort) {
        TrackSorter.sections(sortedCompleted.map { it.track }, sort).map {
            it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT)
        }
    }

    val manifestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importManifest(uri)
    }
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            manifestLauncher.launch(arrayOf("application/json", "text/*"))
        } else {
            Toast.makeText(context, "需要媒体读取权限才能识别清单中的歌曲", Toast.LENGTH_SHORT).show()
        }
    }
    fun requestManifestImport() {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            manifestLauncher.launch(arrayOf("application/json", "text/*"))
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
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
        TrackScreenHeader(title = "下载") {
            // Reserve the trailing slot on both tabs so the title and tab row
            // never move when switching between 已下载 and 下载列表.
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (pagerState.currentPage == 0) {
                    IconButton(
                        onClick = {
                            if (searchMode) {
                                searchMode = false
                                searchQuery = ""
                                keyboard?.hide()
                                exitSelection()
                            } else {
                                searchMode = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searchMode) {
                                Icons.Rounded.Close
                            } else {
                                Icons.Rounded.Search
                            },
                            contentDescription = if (searchMode) "关闭搜索" else "搜索已下载",
                        )
                    }
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { ListTabLabel("已下载") },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { ListTabLabel("下载列表") },
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = !selectionMode,
        ) { page ->
            if (page == 0) {
                DownloadedPage(
                    tasks = sortedCompleted,
                    sections = sections,
                    visibleBytes = visibleBytes,
                    sort = sort,
                    ascending = ascending,
                    searchMode = searchMode,
                    searchQuery = searchQuery,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onQueryChange = { searchQuery = it },
                    onSort = { sort = it },
                    onToggleOrder = { ascending = !ascending },
                    onPlay = { index ->
                        viewModel.play(sortedCompleted, index)
                        onOpenPlayer()
                    },
                    onToggleSelection = { id ->
                        selectedIds = if (id in selectedIds) {
                            selectedIds - id
                        } else {
                            selectedIds + id
                        }
                    },
                    onSelectAll = { selectedIds = sortedCompleted.map { it.id }.toSet() },
                    onStartSelection = { selectionMode = true },
                    onEnterSelection = { id ->
                        selectionMode = true
                        selectedIds = selectedIds + id
                    },
                    onExitSelection = ::exitSelection,
                    onImportManifest = ::requestManifestImport,
                    onDelete = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DownloadQueueList(
                    tasks = active,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                    onDelete = viewModel::remove,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除已下载歌曲") },
            text = {
                Text("将删除 ${selectedIds.size} 首已下载歌曲及其媒体库文件，是否继续？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeMany(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirm = false
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DownloadedPage(
    tasks: List<DownloadTask>,
    sections: List<AlphabetSection>,
    visibleBytes: Long,
    sort: TrackSort,
    ascending: Boolean,
    searchMode: Boolean,
    searchQuery: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    onPlay: (Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onStartSelection: () -> Unit,
    onEnterSelection: (String) -> Unit,
    onExitSelection: () -> Unit,
    onImportManifest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val spec = TrackListHeaderSpec(
        countText = "已下载 ${tasks.size} 首 · ${formatBytes(visibleBytes)}",
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        sort = if (sort in DOWNLOAD_SORT_OPTIONS) sort else DOWNLOAD_SORT_OPTIONS.first(),
        sortOptions = DOWNLOAD_SORT_OPTIONS,
        ascending = ascending,
        categories = null,
    )
    Column(modifier = modifier.fillMaxSize()) {
        if (searchMode) {
            val focusRequester = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                withFrameNanos { }
                keyboard?.show()
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("搜索已下载歌曲") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        TrackListPanel(
            items = tasks,
            itemKey = { it.id },
            validKey = { it.id },
            spec = spec,
            listState = listState,
            sections = sections,
            ascending = ascending,
            selectionMode = selectionMode,
            onStartSelection = onStartSelection,
            onSelect = onToggleSelection,
            onSelectAll = onSelectAll,
            onCancelSelection = onExitSelection,
            onSort = onSort,
            onToggleOrder = onToggleOrder,
            modifier = Modifier.weight(1f),
            countActions = {
                CompactTextButton(text = "导入清单", onClick = onImportManifest)
                if (tasks.isNotEmpty()) {
                    CompactTextButton(text = "选择", onClick = onStartSelection)
                }
            },
            emptyContent = {
                EmptyState(
                    title = if (searchQuery.isBlank()) "还没有已下载歌曲" else "没有匹配的已下载歌曲",
                    description = if (searchQuery.isBlank()) {
                        "在播放页或批量操作中选择下载，完成后会出现在这里。"
                    } else {
                        "换个关键词试试。"
                    },
                    icon = Icons.Rounded.Download,
                )
            },
            footer = {
                Text(
                    text = "下载目录可在设置中修改；Android 10+ 默认写入系统音乐目录，不可写时回退到应用外部音乐目录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            selectionBar = {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "删除所选歌曲及其文件",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = onDelete,
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            },
        ) { index, task ->
            TrackRow(
                track = task.track,
                onClick = {
                    if (selectionMode) onToggleSelection(task.id) else onPlay(index)
                },
                isFavorite = false,
                selected = if (selectionMode) task.id in selectedIds else null,
                onLongClick = if (selectionMode) null else ({ onEnterSelection(task.id) }),
                trailing = {
                    if (task.qualityKbps > 0) {
                        Text(
                            text = qualityTierLabel(task.qualityKbps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun DownloadQueueList(
    tasks: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "没有正在下载的歌曲",
                description = "在播放页或歌曲列表中选择下载后，任务会显示在这里。",
                icon = Icons.Rounded.Download,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.track.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                task.track.artistLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (task.canPause) {
                            IconButton(onClick = { onPause(task.id) }) {
                                Icon(Icons.Rounded.Pause, contentDescription = "暂停")
                            }
                        }
                        if (task.canResume) {
                            IconButton(onClick = { onResume(task.id) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "重试")
                            }
                        }
                        IconButton(onClick = { onCancel(task.id) }) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "取消")
                        }
                        IconButton(onClick = { onDelete(task.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除任务")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(DownloadStatusLabels.label(task.status))
                            if (task.totalBytes > 0) {
                                append(" · ")
                                append(formatBytes(task.bytesDownloaded))
                                append(" / ")
                                append(formatBytes(task.totalBytes))
                            }
                            task.error?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
