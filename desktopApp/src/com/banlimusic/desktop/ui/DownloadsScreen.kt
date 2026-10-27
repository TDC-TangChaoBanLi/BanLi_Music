package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopDownloadTask
import com.banlimusic.shared.ui.components.ListTabLabel
import com.banlimusic.shared.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.shared.ui.components.TrackListHeaderSpec
import com.banlimusic.shared.ui.components.TrackListPanel
import com.banlimusic.shared.ui.components.TrackScreenHeader
import com.banlimusic.shared.ui.components.formatBytes
import java.awt.Desktop
import java.io.File

private val DOWNLOAD_SORT_OPTIONS = listOf(
    TrackSort.CREATED,
    TrackSort.TITLE,
    TrackSort.ARTIST,
)

@Composable
fun DesktopDownloadsScreen(state: DesktopAppState) {
    val tasks by state.downloads.collectAsState()
    val folders by state.favoriteFolders.collectAsState()
    val completed = tasks.filter { it.status == DesktopDownloadStatus.COMPLETED }
    val pending = tasks.filter { it.status != DesktopDownloadStatus.COMPLETED }
    var tab by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TrackSort.CREATED) }
    var ascending by remember { mutableStateOf(true) }
    val completedOrder = remember(completed, sort, ascending) {
        val order = TrackSorter.sort(completed.map { it.track }, sort, ascending)
            .map { it.id }
            .withIndex()
            .associate { it.value to it.index }
        completed.sortedBy { order[it.track.id] ?: Int.MAX_VALUE }
    }
    val sections = remember(completedOrder, sort) {
        TrackSorter.sections(completedOrder.map { it.track }, sort).map {
            it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT)
        }
    }
    val totalBytes = remember(completedOrder) { completedOrder.sumOf { it.totalBytes } }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        TrackScreenHeader(title = "下载") {
            if (completed.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("清除已下载")
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
            TrackListPanel(
                items = completedOrder,
                itemKey = { it.id },
                validKey = { it.id },
                spec = TrackListHeaderSpec(
                    countText = "已下载 ${completedOrder.size} 首 · ${formatBytes(totalBytes)}",
                    sort = sort,
                    sortOptions = DOWNLOAD_SORT_OPTIONS,
                    ascending = ascending,
                ),
                listState = listState,
                sections = sections,
                ascending = ascending,
                selectionMode = false,
                onStartSelection = {},
                onSelect = {},
                onSelectAll = {},
                onCancelSelection = {},
                onSort = { sort = it },
                onToggleOrder = { ascending = !ascending },
                emptyContent = {
                    DesktopEmptyState(
                        title = "还没有已下载歌曲",
                        description = "在搜索、音乐库或播放页点击下载按钮，文件会保存到下载目录。",
                    )
                },
                footer = {
                    Text(
                        text = "下载目录可在设置中修改；桌面端当前使用 JLayer 播放 MP3。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            ) { _, task ->
                DownloadCard(task = task, state = state, categoryLabel = folders[task.track.id])
            }
        } else {
            if (pending.isEmpty()) {
                DesktopEmptyState(
                    title = "没有进行中的下载",
                    description = "下载任务会显示在这里，支持取消和失败重试。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(pending, key = { it.id }) { task ->
                        DownloadCard(
                            task = task,
                            state = state,
                            categoryLabel = folders[task.track.id],
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除已下载歌曲") },
            text = { Text("将删除 ${completed.size} 个已下载文件及其歌词/海报，是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        state.container.downloads.clearCompleted()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DownloadCard(
    task: DesktopDownloadTask,
    state: DesktopAppState,
    categoryLabel: String?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopTrackArtwork(
                    track = task.track,
                    loader = state.container.artworkLoader,
                    allowRemote = false,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.track.artistLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusLabel(task.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (task.status) {
                                DesktopDownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                DesktopDownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                DesktopDownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = task.track.sources.firstOrNull()?.providerId
                                ?.let(::desktopProviderLabel) ?: "未知来源",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${task.qualityKbps}K",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        categoryLabel?.takeIf { it.isNotBlank() }?.let { folder ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = folder,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (task.bytesDownloaded > 0L) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatBytes(task.bytesDownloaded) +
                                    if (task.totalBytes > 0L) " / ${formatBytes(task.totalBytes)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                when (task.status) {
                    DesktopDownloadStatus.QUEUED,
                    DesktopDownloadStatus.DOWNLOADING -> TextButton(
                        onClick = { state.container.downloads.cancel(task.id) },
                    ) { Text("取消") }

                    DesktopDownloadStatus.FAILED,
                    DesktopDownloadStatus.CANCELLED -> TextButton(
                        onClick = { state.container.downloads.retry(task.id) },
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("重试")
                    }

                    DesktopDownloadStatus.COMPLETED -> {
                        TextButton(
                            onClick = {
                                task.file?.let { state.playLocal(task.track, it) }
                            },
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("播放")
                        }
                        TextButton(
                            onClick = { task.file?.let(::openInFileManager) },
                        ) { Text("打开目录") }
                        TextButton(
                            onClick = { state.container.downloads.remove(task.id) },
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (task.status == DesktopDownloadStatus.DOWNLOADING ||
                task.status == DesktopDownloadStatus.QUEUED
            ) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            task.error?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            task.filePath?.let { path ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun statusLabel(status: DesktopDownloadStatus): String = when (status) {
    DesktopDownloadStatus.QUEUED -> "等待中"
    DesktopDownloadStatus.DOWNLOADING -> "下载中"
    DesktopDownloadStatus.COMPLETED -> "已完成"
    DesktopDownloadStatus.FAILED -> "失败"
    DesktopDownloadStatus.CANCELLED -> "已取消"
}

private fun openInFileManager(file: File) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(file.parentFile)
        }
    }
}
