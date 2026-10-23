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

    fun clearCompleted() = viewModelScope.launch {
        downloads.clearCompleted()
    }

    fun play(task: DownloadTask) {
        val file = task.file ?: return
        if (!file.exists()) return
        playback.playFile(task.track, file)
    }
}
