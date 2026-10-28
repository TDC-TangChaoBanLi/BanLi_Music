package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.banlimusic.desktop.DesktopPaths
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.shared.ui.components.formatBytes
import com.banlimusic.shared.ui.screens.BanLiDownloadsScreen
import com.banlimusic.shared.ui.screens.DownloadItemStatus
import com.banlimusic.shared.ui.screens.DownloadItemUi
import com.banlimusic.shared.ui.screens.DownloadsActions
import com.banlimusic.shared.ui.screens.DownloadsUiState
import java.io.File
import javax.swing.JFileChooser

/** Desktop adapter for the shared download screen. */
@Composable
fun DesktopDownloadsScreen(state: DesktopAppState) {
    val tasks by state.downloads.collectAsState()
    val settings by state.settings.collectAsState()
    val completed = remember(tasks) {
        tasks.filter { it.status == DesktopDownloadStatus.COMPLETED }
    }
    val actions = remember(state, tasks) {
        object : DownloadsActions {
            override fun play(item: DownloadItemUi) {
                val task = state.downloads.value.firstOrNull { it.id == item.id } ?: return
                task.file?.let { state.playLocal(task.track, it) }
            }

            override fun pause(id: String) = state.container.downloads.pause(id)

            override fun resume(id: String) = state.container.downloads.retry(id)

            override fun cancel(id: String) = state.container.downloads.cancel(id)

            override fun remove(id: String) = state.container.downloads.remove(id)

            override fun delete(items: List<DownloadItemUi>) {
                items.forEach { state.container.downloads.remove(it.id) }
            }

            override fun importManifest() {
                chooseManifest()?.let(state::importManifest)
            }
        }
    }

    BanLiDownloadsScreen(
        state = DownloadsUiState(
            items = tasks.map { task ->
                DownloadItemUi(
                    id = task.id,
                    track = task.track,
                    qualityKbps = task.qualityKbps,
                    status = task.status.toUiStatus(),
                    bytesDownloaded = task.bytesDownloaded,
                    totalBytes = task.totalBytes,
                    error = task.error,
                    createdAt = task.createdAt,
                    canPause = task.canPause,
                    canResume = task.canResume,
                )
            },
            directoryHint = buildString {
                append("已下载 ${completed.size} 首 · ")
                append(formatBytes(completed.sumOf { it.totalBytes }))
                append("。目录：")
                append(
                    settings.downloadDirectory.ifBlank {
                        DesktopPaths.downloadDir.toString()
                    },
                )
            },
            immediateDragSelection = true,
        ),
        actions = actions,
        artwork = { track, artworkModifier ->
            DesktopTrackArtwork(
                track = track,
                loader = state.container.artworkLoader,
                allowRemote = false,
                modifier = artworkModifier,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun DesktopDownloadStatus.toUiStatus(): DownloadItemStatus = when (this) {
    DesktopDownloadStatus.QUEUED -> DownloadItemStatus.QUEUED
    DesktopDownloadStatus.DOWNLOADING -> DownloadItemStatus.DOWNLOADING
    DesktopDownloadStatus.PAUSED -> DownloadItemStatus.PAUSED
    DesktopDownloadStatus.COMPLETED -> DownloadItemStatus.COMPLETED
    DesktopDownloadStatus.FAILED -> DownloadItemStatus.FAILED
    DesktopDownloadStatus.CANCELLED -> DownloadItemStatus.CANCELLED
}

private fun chooseManifest(): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = "导入下载清单"
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "下载清单 (JSON)",
            "json",
        )
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}
