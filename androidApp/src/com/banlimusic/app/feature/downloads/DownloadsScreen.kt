package com.banlimusic.app.feature.downloads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.banlimusic.app.ui.components.formatBytes

@Composable
fun DownloadsScreen(
    onOpenPlayer: () -> Unit,
    viewModel: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val completedBytes = tasks
        .filter { it.status == DownloadStatus.COMPLETED }
        .sumOf { it.totalBytes }

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
                    text = "已占用 ${formatBytes(completedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.any { it.status == DownloadStatus.COMPLETED }) {
                TextButton(onClick = viewModel::clearCompleted) { Text("清除已完成") }
            }
        }

        if (tasks.isEmpty()) {
            EmptyState(
                title = "没有下载任务",
                description = "在播放页点击下载按钮，任务会在这里显示并支持暂停与续传。",
                icon = Icons.Rounded.Download,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadCard(
                        task = task,
                        onPlay = {
                            if (task.file?.exists() == true) {
                                viewModel.play(task)
                                onOpenPlayer()
                            }
                        },
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onCancel = { viewModel.cancel(task.id) },
                        onDelete = { viewModel.remove(task.id) },
                    )
                }
                item {
                    Text(
                        text = "下载的音乐仅保存在应用私有目录，供个人学习使用。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    task: DownloadTask,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val isCompleted = task.status == DownloadStatus.COMPLETED
    Card(
        onClick = { if (isCompleted) onPlay() },
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
                if (isCompleted) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "播放")
                    }
                } else if (task.canPause) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = "暂停")
                    }
                } else if (task.canResume) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "继续下载")
                    }
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
                Spacer(Modifier.weight(1f))
                val sizeText = when {
                    task.status == DownloadStatus.COMPLETED && task.totalBytes > 0 ->
                        formatBytes(task.totalBytes)

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
            if (!isCompleted) {
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
