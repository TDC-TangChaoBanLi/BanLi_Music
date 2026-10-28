/**
 * 文件作用：管理下载页状态以及播放、暂停、恢复和删除操作。
 */
package com.banlimusic.app.feature.downloads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.data.repository.DownloadManifestRepository
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.ManifestImportResult
import com.banlimusic.app.domain.download.DownloadTask
import com.banlimusic.app.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloads: DownloadRepository,
    private val playback: PlaybackController,
    private val manifest: DownloadManifestRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val tasks: StateFlow<List<DownloadTask>> = downloads.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteFolders: StateFlow<Map<String, Set<String>>> = library.observeFavoriteFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun pause(id: String) = viewModelScope.launch { downloads.pause(id) }

    fun resume(id: String) = viewModelScope.launch { downloads.resume(id) }

    fun cancel(id: String) = viewModelScope.launch { downloads.cancel(id) }

    fun remove(id: String) = viewModelScope.launch {
        downloads.remove(id)
    }

    fun removeMany(ids: Collection<String>) = viewModelScope.launch {
        ids.forEach { downloads.remove(it) }
    }

    fun clearCompleted() = viewModelScope.launch {
        downloads.clearCompleted()
    }

    /** Imports `banli-downloads.json` chosen through SAF. */
    fun importManifest(uri: Uri) = viewModelScope.launch {
        val result = manifest.importManifest(uri)
        _message.value = importMessage(result)
    }

    /** Retries the silent manifest sync for an app-readable download directory. */
    fun refreshManifest() = viewModelScope.launch {
        runCatching {
            val count = manifest.syncFromTarget(downloads.downloadTarget())
            if (count > 0) _message.value = "已识别 $count 首下载目录中的歌曲"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun importMessage(result: ManifestImportResult): String = when {
        result.failed -> "无法读取该清单文件"
        result.total == 0 -> "清单中没有歌曲"
        result.imported == 0 && result.missing > 0 ->
            "清单中的 ${result.missing} 首歌曲未在媒体库中找到"
        result.missing > 0 ->
            "已导入 ${result.imported} 首，${result.missing} 首未找到文件"
        else -> "已导入 ${result.imported} 首歌曲"
    }

    fun play(tasks: List<DownloadTask>, index: Int) {
        val tracks = tasks.map { it.track }
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(tracks.indices)
        // Keep the whole downloaded list as the hidden playback queue; local files
        // are preferred automatically by PlaybackController.
        playback.play(tracks[safeIndex], tracks)
    }
}
