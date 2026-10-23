package com.banlimusic.app.feature.downloads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.domain.download.DownloadStatus
import com.banlimusic.app.domain.download.DownloadStatusLabels
import com.banlimusic.app.domain.download.DownloadTask
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.app.ui.components.formatBytes
import com.banlimusic.app.ui.components.qualityLabel

@Composable
fun DownloadsScreen(
    onOpenPlayer: () -> Unit,
    viewModel: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val active = tasks.filter { it.status != DownloadStatus.COMPLETED }
    val completedBytes = completed.sumOf { it.totalBytes }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "下载", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "已下载 ${completed.size} 首 · ${formatBytes(completedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedTab == 0 && completed.isNotEmpty() && !selectionMode) {
                TextButton(onClick = { selectionMode = true }) { Text("选择") }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    selectionMode = false
                    selectedIds = emptySet()
                },
                text = { Text("已下载") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    selectionMode = false
                    selectedIds = emptySet()
                },
                text = { Text("下载列表") },
            )
        }

        if (selectedTab == 0) {
            DownloadedList(
                tasks = completed,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onPlay = { task ->
                    viewModel.play(task)
                    onOpenPlayer()
                },
                onToggleSelection = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                onEnterSelection = { id ->
                    selectionMode = true
                    selectedIds = selectedIds + id
                },
                modifier = Modifier.weight(1f),
            )
            if (selectionMode) {
                DeleteSelectionBar(
                    count = selectedIds.size,
                    onSelectAll = { selectedIds = completed.map { it.id }.toSet() },
                    onDelete = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                    onCancel = {
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                )
            }
        } else {
            DownloadQueueList(
                tasks = active,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCancel = viewModel::cancel,
                onDelete = viewModel::remove,
                modifier = Modifier.weight(1f),
            )
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
private fun DownloadedList(
    tasks: List<DownloadTask>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onPlay: (DownloadTask) -> Unit,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "还没有已下载歌曲",
                description = "在播放页或批量操作中选择下载，完成后会出现在这里。",
                icon = Icons.Rounded.Download,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(tasks, key = { _, task -> task.id }) { _, task ->
            TrackRow(
                track = task.track,
                onClick = {
                    if (selectionMode) onToggleSelection(task.id) else onPlay(task)
                },
                isFavorite = false,
                selected = if (selectionMode) task.id in selectedIds else null,
                onLongClick = if (selectionMode) {
                    null
                } else {
                    { onEnterSelection(task.id) }
                },
                trailing = {
                    if (task.qualityKbps > 0) {
                        Text(
                            text = qualityLabel(task.qualityKbps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        item {
            Text(
                text = "下载目录可在设置中修改；Android 10+ 默认写入系统音乐目录，不可写时回退到应用外部音乐目录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DeleteSelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 $count 首",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) { Text("全选") }
            TextButton(onClick = onDelete, enabled = count > 0) { Text("删除") }
            TextButton(onClick = onCancel) { Text("取消") }
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
                title = "下载列表为空",
                description = "正在下载、等待中或失败的任务会显示在这里。",
                icon = Icons.Rounded.Download,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            DownloadCard(
                task = task,
                onPause = { onPause(task.id) },
                onResume = { onResume(task.id) },
                onCancel = { onCancel(task.id) },
                onDelete = { onDelete(task.id) },
            )
        }
        item {
            Text(
                text = "批量下载会经过共享限流器，GD 音乐台请求间隔至少 1 秒。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DownloadCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(
                    track = task.track,
                    modifier = Modifier.size(50.dp),
                    cornerRadius = 10.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = task.track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = task.track.artistLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                when {
                    task.canPause -> IconButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = "暂停")
                    }

                    task.canResume -> IconButton(onClick = onResume) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "继续下载")
                    }

                    else -> Unit
                }
                IconButton(onClick = if (task.isActive) onCancel else onDelete) {
                    Icon(
                        imageVector = if (task.isActive) Icons.Rounded.Cancel else Icons.Rounded.Delete,
                        contentDescription = if (task.isActive) "取消" else "删除",
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DownloadStatusLabels.label(task.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (task.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (task.qualityKbps > 0) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = qualityLabel(task.qualityKbps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                val sizeText = when {
                    task.totalBytes > 0 ->
                        "${formatBytes(task.bytesDownloaded)} / ${formatBytes(task.totalBytes)}"

                    else -> formatBytes(task.bytesDownloaded)
                }
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (task.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            if (!task.error.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = task.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
