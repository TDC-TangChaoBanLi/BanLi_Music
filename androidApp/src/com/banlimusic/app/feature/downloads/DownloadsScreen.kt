/**
 * 文件作用：下载页的 Android 适配层，向共享下载界面提供任务状态与平台文件选择。
 */
package com.banlimusic.app.feature.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.domain.download.DownloadStatus
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.shared.ui.components.formatBytes
import com.banlimusic.shared.ui.screens.BanLiDownloadsScreen
import com.banlimusic.shared.ui.screens.DownloadItemStatus
import com.banlimusic.shared.ui.screens.DownloadItemUi
import com.banlimusic.shared.ui.screens.DownloadsActions
import com.banlimusic.shared.ui.screens.DownloadsScreenController
import com.banlimusic.shared.ui.screens.DownloadsUiState

@Composable
fun DownloadsScreen(
    onOpenPlayer: () -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val controller = remember { DownloadsScreenController() }

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

    BackHandler { if (!controller.handleBack()) onExitApp() }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val completedTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.COMPLETED }
    }
    val actions = remember(viewModel, tasks, onOpenPlayer) {
        object : DownloadsActions {
            override fun play(item: DownloadItemUi) {
                val list = tasks.filter { it.status == DownloadStatus.COMPLETED }
                val index = list.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    viewModel.play(list, index)
                    onOpenPlayer()
                }
            }

            override fun pause(id: String) { viewModel.pause(id) }

            override fun resume(id: String) { viewModel.resume(id) }

            override fun cancel(id: String) { viewModel.cancel(id) }

            override fun remove(id: String) { viewModel.remove(id) }

            override fun delete(items: List<DownloadItemUi>) {
                viewModel.removeMany(items.map { it.id })
            }

            override fun importManifest() = requestManifestImport()
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
                append("已下载 ${completedTasks.size} 首 · ")
                append(formatBytes(completedTasks.sumOf { it.totalBytes }))
                append("。下载目录可在设置中修改。")
            },
        ),
        actions = actions,
        artwork = { track, artworkModifier ->
            TrackArtwork(
                track = track,
                modifier = artworkModifier,
                requestRemoteArtwork = false,
            )
        },
        modifier = Modifier.fillMaxSize(),
        controller = controller,
    )
}

private fun DownloadStatus.toUiStatus(): DownloadItemStatus = when (this) {
    DownloadStatus.QUEUED -> DownloadItemStatus.QUEUED
    DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING -> DownloadItemStatus.DOWNLOADING
    DownloadStatus.PAUSED -> DownloadItemStatus.PAUSED
    DownloadStatus.COMPLETED -> DownloadItemStatus.COMPLETED
    DownloadStatus.FAILED -> DownloadItemStatus.FAILED
    DownloadStatus.CANCELLED -> DownloadItemStatus.CANCELLED
}
