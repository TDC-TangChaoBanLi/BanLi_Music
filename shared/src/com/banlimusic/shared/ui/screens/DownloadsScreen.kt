/**
 * 文件作用：两端共用的下载页面，包含已下载列表、下载队列、搜索、排序与批量删除。
 */
package com.banlimusic.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.sort.AlphabetSection
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiTrackListRow
import com.banlimusic.shared.ui.components.CompactTextButton
import com.banlimusic.shared.ui.components.ListTabLabel
import com.banlimusic.shared.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.shared.ui.components.TrackListHeaderSpec
import com.banlimusic.shared.ui.components.TrackListPanel
import com.banlimusic.shared.ui.components.TrackScreenHeader
import com.banlimusic.shared.ui.components.formatBytes
import com.banlimusic.shared.ui.components.qualityTierLabel
import com.banlimusic.shared.ui.components.rememberTrackSelectionState

enum class DownloadItemStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED,
}

/** Platform-independent download row. */
data class DownloadItemUi(
    val id: String,
    val track: Track,
    val qualityKbps: Int,
    val status: DownloadItemStatus,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
    val createdAt: Long = 0L,
    val canPause: Boolean = false,
    val canResume: Boolean = false,
) {
    val isCompleted: Boolean get() = status == DownloadItemStatus.COMPLETED
    val isActive: Boolean
        get() = status == DownloadItemStatus.QUEUED || status == DownloadItemStatus.DOWNLOADING
    val progress: Float
        get() = when {
            isCompleted -> 1f
            totalBytes <= 0L -> 0f
            else -> (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
}

data class DownloadsUiState(
    val items: List<DownloadItemUi> = emptyList(),
    val directoryHint: String = "",
    /** Desktop uses mouse drag selection instead of long-press drag selection. */
    val immediateDragSelection: Boolean = false,
)

interface DownloadsActions {
    fun play(item: DownloadItemUi)
    fun pause(id: String) {}
    fun resume(id: String) {}
    fun cancel(id: String) {}
    fun remove(id: String) {}
    fun delete(items: List<DownloadItemUi>) {}
    fun importManifest() {}
}

/** Same pattern as the library: platform back is forwarded into local UI state. */
class DownloadsScreenController {
    internal var backHandler: (() -> Boolean)? = null
    fun handleBack(): Boolean = backHandler?.invoke() == true
}

private val DOWNLOAD_SORT_OPTIONS = listOf(
    TrackSort.CREATED,
    TrackSort.TITLE,
    TrackSort.ARTIST,
)

private fun List<AlphabetSection>.withListHeaderOffset(): List<AlphabetSection> =
    map { it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT) }

private fun statusLabel(status: DownloadItemStatus): String = when (status) {
    DownloadItemStatus.QUEUED -> "等待中"
    DownloadItemStatus.DOWNLOADING -> "下载中"
    DownloadItemStatus.COMPLETED -> "已完成"
    DownloadItemStatus.FAILED -> "失败"
    DownloadItemStatus.CANCELLED -> "已取消"
    DownloadItemStatus.PAUSED -> "已暂停"
}

@Composable
fun BanLiDownloadsScreen(
    state: DownloadsUiState,
    actions: DownloadsActions,
    artwork: @Composable (Track, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    controller: DownloadsScreenController = remember { DownloadsScreenController() },
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TrackSort.CREATED) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val selection = rememberTrackSelectionState()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    val completed = remember(state.items) { state.items.filter { it.isCompleted } }
    val active = remember(state.items) { state.items.filterNot { it.isCompleted } }
    val normalizedQuery = remember(searchQuery) { TextNormalizer.normalize(searchQuery) }
    val visible = remember(completed, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            completed
        } else {
            completed.filter { item ->
                listOfNotNull(
                    item.track.title,
                    item.track.album?.title,
                    item.track.artistLine,
                ).any { TextNormalizer.normalize(it).contains(normalizedQuery) }
            }
        }
    }
    val sorted = remember(visible, sort, ascending) {
        val order = TrackSorter.sort(visible.map { it.track }, sort, ascending)
            .map { it.id }
            .withIndex()
            .associate { it.value to it.index }
        visible.sortedWith(
            compareBy({ order[it.track.id] ?: Int.MAX_VALUE }, { it.createdAt }),
        )
    }
    val sections = remember(sorted, sort) {
        TrackSorter.sections(sorted.map { it.track }, sort).withListHeaderOffset()
    }
    val visibleBytes = remember(sorted) { sorted.sumOf { it.totalBytes } }
    val selectedItems = sorted.filter { it.id in selection.selectedIds }

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
                tab != 0 -> {
                    tab = 0
                    true
                }
                else -> false
            }
        }
        onDispose { controller.backHandler = null }
    }
    LaunchedEffect(tab) { exitSelection() }
    LaunchedEffect(sorted) {
        selection.retain(sorted.mapTo(mutableSetOf()) { it.id })
    }

    Column(modifier = modifier.fillMaxSize()) {
        TrackScreenHeader(title = "下载") {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (tab == 0) {
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
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { ListTabLabel("已下载") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { ListTabLabel("下载列表") },
            )
        }

        if (tab == 0) {
            Column(modifier = Modifier.weight(1f)) {
                if (searchMode) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        withFrameNanos { }
                        keyboard?.show()
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
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
                    items = sorted,
                    itemKey = { it.id },
                    validKey = { it.id },
                    spec = TrackListHeaderSpec(
                        countText = "已下载 ${sorted.size} 首 · ${formatBytes(visibleBytes)}",
                        selectionMode = selection.mode,
                        selectedCount = selection.selectedIds.size,
                        sort = if (sort in DOWNLOAD_SORT_OPTIONS) sort else DOWNLOAD_SORT_OPTIONS.first(),
                        sortOptions = DOWNLOAD_SORT_OPTIONS,
                        ascending = ascending,
                        categories = null,
                    ),
                    listState = listState,
                    sections = sections,
                    ascending = ascending,
                    selectionMode = selection.mode,
                    onStartSelection = selection::enter,
                    onSelect = selection::toggle,
                    onSelectAll = { selection.selectAll(sorted.map { it.id }) },
                    onCancelSelection = selection::exit,
                    onSort = { sort = it },
                    onToggleOrder = { ascending = !ascending },
                    modifier = Modifier.weight(1f),
                    immediateDragSelection = state.immediateDragSelection,
                    countActions = {
                        CompactTextButton(text = "导入清单", onClick = actions::importManifest)
                        if (sorted.isNotEmpty()) {
                            CompactTextButton(text = "选择", onClick = selection::enter)
                        }
                    },
                    emptyContent = {
                        BanLiEmptyState(
                            title = if (searchQuery.isBlank()) {
                                "还没有已下载歌曲"
                            } else {
                                "没有匹配的已下载歌曲"
                            },
                            description = if (searchQuery.isBlank()) {
                                "在播放页或批量操作中选择下载，完成后会出现在这里。"
                            } else {
                                "换个关键词试试。"
                            },
                            icon = Icons.Rounded.Download,
                        )
                    },
                    footer = {
                        if (state.directoryHint.isNotBlank()) {
                            Text(
                                text = state.directoryHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    },
                    selectionBar = {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                        ) {
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides 0.dp,
                            ) {
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
                                        onClick = { showDeleteConfirm = true },
                                        enabled = selection.selectedIds.isNotEmpty(),
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
                ) { _, item ->
                    BanLiTrackListRow(
                        track = item.track,
                        artwork = { artwork(item.track, it) },
                        onClick = {
                            if (selection.mode) selection.toggle(item.id)
                            else actions.play(item)
                        },
                        onLongClick = { selection.enter(); selection.toggle(item.id) },
                        selected = if (selection.mode) item.id in selection.selectedIds else null,
                        extraBadges = {
                            Text(
                                text = qualityTierLabel(item.qualityKbps),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        } else {
            DownloadQueueList(
                items = active,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除已下载歌曲") },
            text = { Text("将删除 ${selectedItems.size} 首已下载歌曲及其文件，是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.delete(selectedItems)
                        showDeleteConfirm = false
                        selection.exit()
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
private fun DownloadQueueList(
    items: List<DownloadItemUi>,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BanLiEmptyState(
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
        items(items, key = { it.id }) { item ->
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.track.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                item.track.artistLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = qualityTierLabel(item.qualityKbps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (item.canPause) {
                            IconButton(onClick = { actions.pause(item.id) }) {
                                Icon(Icons.Rounded.Pause, contentDescription = "暂停")
                            }
                        }
                        if (item.canResume) {
                            IconButton(onClick = { actions.resume(item.id) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "继续")
                            }
                        }
                        IconButton(onClick = { actions.cancel(item.id) }) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "取消")
                        }
                        IconButton(onClick = { actions.remove(item.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除任务")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(statusLabel(item.status))
                            if (item.totalBytes > 0) {
                                append(" · ")
                                append(formatBytes(item.bytesDownloaded))
                                append(" / ")
                                append(formatBytes(item.totalBytes))
                            }
                            item.error?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
