package com.banlimusic.app.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.LocalMusicRepository
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.domain.download.DownloadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val library: LibraryRepository,
    private val localMusic: LocalMusicRepository,
    private val downloads: DownloadRepository,
    private val music: MusicRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val favorites: StateFlow<List<Track>> = _selectedCategory
        .flatMapLatest { category -> library.observeFavorites(category) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = library.observeFavoriteCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = library.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** trackId -> favourite folder, so lists can show where a track lives. */
    val favoriteFolders: StateFlow<Map<String, String>> = library.observeFavoriteFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val history: StateFlow<List<Track>> = library.observeHistory(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Application track ids that already have a completed download on disk. */
    val downloadedIds: StateFlow<Set<String>> = downloads.observeTasks()
        .map { tasks ->
            tasks
                .filter { it.status == DownloadStatus.COMPLETED && !it.filePath.isNullOrBlank() }
                .map { it.track.id }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val localTracks: StateFlow<List<Track>> = combine(
        localMusic.observeTracks(),
        downloads.observeTasks(),
    ) { imported, tasks ->
        val downloaded = tasks
            .filter { it.status == DownloadStatus.COMPLETED && !it.filePath.isNullOrBlank() }
            .map { task ->
                val uri = task.filePath.orEmpty()
                // Keep the original provider sources so artwork and lyrics can still
                // be resolved on demand even when the sidecar files were not saved.
                val localSource = TrackSource(
                    providerId = "download",
                    providerTrackId = uri,
                    availableActions = setOf(TrackAction.STREAM),
                    localUri = uri,
                    durationMs = task.track.durationMs,
                )
                task.track.copy(
                    sources = listOf(localSource) + task.track.sources.filter { it.localUri == null },
                )
            }
        (imported + downloaded).distinctBy { track ->
            track.sources.firstNotNullOfOrNull { it.localUri } ?: track.id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun importLocalFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = runCatching { localMusic.importFromUris(uris) }
                .getOrDefault(0)
            _message.value = if (count > 0) {
                "已导入 $count 首本地音乐"
            } else {
                "所选文件中没有可导入的歌曲（过短或格式不支持）"
            }
        }
    }

    fun importLocalFolder(treeUri: Uri) {
        viewModelScope.launch {
            val count = runCatching { localMusic.importFromTree(treeUri) }
                .getOrDefault(0)
            _message.value = if (count > 0) {
                "已从文件夹导入 $count 首歌曲"
            } else {
                "该文件夹中没有可导入的歌曲"
            }
        }
    }

    fun deleteLocalTracks(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            localMusic.delete(ids)
            _message.value = "已移除 ${ids.size} 首本地记录（不会删除原始文件）"
        }
    }

    /**
     * Removes the selected imported records. Downloaded tracks are not touched:
     * their files belong to the download page, so the user is told where to go.
     */
    fun deleteLocalSelection(tracks: List<Track>) {
        val selected = tracks.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return
        val imported = selected.filter { track ->
            track.sources.any { it.providerId == "local" }
        }
        val downloadedCount = selected.size - imported.size
        viewModelScope.launch {
            if (imported.isNotEmpty()) localMusic.delete(imported.map { it.id })
            _message.value = buildString {
                if (imported.isNotEmpty()) {
                    append("已移除 ${imported.size} 首导入记录（未删除原始文件）")
                }
                if (downloadedCount > 0) {
                    if (isNotEmpty()) append("；")
                    append("$downloadedCount 首为已下载歌曲，如需删除请到 下载-已下载 中删除源文件")
                }
            }
            clearSelection()
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

    suspend fun downloadQualities(tracks: List<Track>): List<Int> =
        runCatching { music.downloadQualities(tracks) }.getOrDefault(emptyList())

    fun addFavorites(tracks: List<Track>, category: String) {
        val selected = tracks.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            library.addFavorites(selected, category)
            _message.value = "已收藏 ${selected.size} 首到“$category”"
            clearSelection()
        }
    }

    fun downloadSelected(tracks: List<Track>, qualityKbps: Int? = null) {
        val selected = tracks.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val queued = downloads.enqueueAll(selected, qualityKbps = qualityKbps)
            _message.value = "已加入下载队列：$queued 首"
            clearSelection()
        }
    }

    fun createCategory(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            library.ensureCategory(normalized)
            _message.value = "已创建收藏夹“$normalized”"
        }
    }

    suspend fun exportFavoritesJson(): String = library.exportFavoritesJson()

    fun importFavoritesJson(raw: String, replace: Boolean) {
        viewModelScope.launch {
            val count = runCatching { library.importFavoritesJson(raw, replace) }
                .getOrDefault(0)
            _message.value = if (replace) {
                "已覆盖导入 $count 首收藏"
            } else {
                "已追加 $count 首收藏"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
