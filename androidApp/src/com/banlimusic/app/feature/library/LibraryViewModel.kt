package com.banlimusic.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.LocalMusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val library: LibraryRepository,
    private val localMusic: LocalMusicRepository,
    private val downloads: DownloadRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val favorites: StateFlow<List<Track>> = _selectedCategory
        .flatMapLatest { category -> library.observeFavorites(category) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = library.observeFavoriteCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<Track>> = library.observeHistory(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localTracks: StateFlow<List<Track>> = localMusic.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultCategory: StateFlow<String> = settings.settings
        .map { it.defaultFavoriteCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "默认收藏")

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setCategory(category: String?) {
        _selectedCategory.value = category
        _selectedIds.value = emptySet()
    }

    fun removeFavorite(track: Track) {
        viewModelScope.launch { library.removeFavorite(track.id) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    fun importLocalMusic() {
        viewModelScope.launch {
            val count = runCatching { localMusic.importAll() }
                .getOrDefault(0)
            _message.value = if (count > 0) {
                "已导入 $count 首本地音乐"
            } else {
                "没有发现新的本地音乐"
            }
        }
    }

    fun clearLocalMusic() {
        viewModelScope.launch {
            localMusic.clear()
            _message.value = "已清空本地音乐记录（不会删除原始文件）"
        }
    }

    fun toggleSelection(trackId: String) {
        _selectedIds.value = if (trackId in _selectedIds.value) {
            _selectedIds.value - trackId
        } else {
            _selectedIds.value + trackId
        }
    }

    fun selectAll(tracks: List<Track>) {
        _selectedIds.value = tracks.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun downloadSelected(tracks: List<Track>) {
        val selected = tracks.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val queued = downloads.enqueueAll(selected)
            _message.value = "已加入下载队列：$queued 首"
            clearSelection()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
