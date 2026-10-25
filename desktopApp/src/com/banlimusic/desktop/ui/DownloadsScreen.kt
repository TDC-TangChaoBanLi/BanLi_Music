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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopDownloadTask
import java.awt.Desktop
import java.io.File

@Composable
fun DesktopDownloadsScreen(state: DesktopAppState) {
    val tasks by state.downloads.collectAsState()
    val completed = tasks.filter { it.status == DesktopDownloadStatus.COMPLETED }
    val pending = tasks.filter { it.status != DesktopDownloadStatus.COMPLETED }
    var tab by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "下载",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "已下载 ${completed.size} 首，队列中 ${pending.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (completed.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("清除已下载")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text("已下载") },
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text("下载列表") },
            )
        }
        Spacer(Modifier.height(12.dp))

        val visible = if (tab == 0) completed else pending
        if (visible.isEmpty()) {
            DesktopEmptyState(
                title = if (tab == 0) "还没有已下载歌曲" else "没有进行中的下载",
                description = if (tab == 0) {
                    "在搜索、音乐库或播放页点击下载按钮，文件会保存到下载目录。"
                } else {
                    "下载任务会显示在这里，支持取消和失败重试。"
                },
                glyph = "↓",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(visible, key = { it.id }) { task ->
                    DownloadCard(
                        task = task,
                        state = state,
                    )
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
private fun DownloadCard(task: DesktopDownloadTask, state: DesktopAppState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                            text = "${task.qualityKbps}K",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
