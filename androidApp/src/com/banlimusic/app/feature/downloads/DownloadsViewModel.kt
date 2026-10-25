/**
 * 文件作用：管理下载页状态以及播放、暂停、恢复和删除操作。
 */
package com.banlimusic.app.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.domain.download.DownloadTask
import com.banlimusic.app.player.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloads: DownloadRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = downloads.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun play(tasks: List<DownloadTask>, index: Int) {
        val tracks = tasks.map { it.track }
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(tracks.indices)
        // Keep the whole downloaded list as the hidden playback queue; local files
        // are preferred automatically by PlaybackController.
        playback.play(tracks[safeIndex], tracks)
    }
}
