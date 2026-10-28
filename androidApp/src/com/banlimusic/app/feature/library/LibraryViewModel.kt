/**
 * 文件作用：管理音乐库数据、收藏、批量操作与本地音乐扫描状态。
 */
package com.banlimusic.app.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.datastore.SourcePreferenceStore
import com.banlimusic.app.core.datastore.orderFavoriteCategories
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
import kotlinx.coroutines.flow.first
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
    private val settings: SettingsRepository,
    private val sourcePreferenceStore: SourcePreferenceStore,
) : ViewModel() {

    /** trackId → preferred provider selected from the source picker. */
    val preferredSources: StateFlow<Map<String, String>> = sourcePreferenceStore.preferredSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val favorites: StateFlow<List<Track>> = _selectedCategory
        .flatMapLatest { category -> library.observeFavorites(category) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unfiltered favourites used by the library search. */
    val allFavorites: StateFlow<List<Track>> = library.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Folder list honouring the user's drag order; unknown folders append by name. */
    val categories: StateFlow<List<String>> = combine(
        library.observeFavoriteCategories(),
        settings.settings,
    ) { folders, appSettings ->
        orderFavoriteCategories(folders, appSettings.favoriteCategoryOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = library.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** trackId -> every favourite folder, so lists can show where a track lives. */
    val favoriteFolders: StateFlow<Map<String, Set<String>>> = library.observeFavoriteFolders()
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

    /** Tracks imported from MediaStore or SAF. */
    val importedTracks: StateFlow<List<Track>> = localMusic.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tracks whose download finished, including entries imported from a manifest. */
    val downloadedTracks: StateFlow<List<Track>> = downloads.observeTasks()
        .map { tasks ->
            tasks
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
                        sources = listOf(localSource) +
                            task.track.sources.filter { it.localUri == null },
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedBytes: StateFlow<Long> = downloads.observeTasks()
        .map { tasks ->
            tasks
                .filter { it.status == DownloadStatus.COMPLETED }
                .sumOf { it.totalBytes }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val localTracks: StateFlow<List<Track>> = combine(
        importedTracks,
        downloadedTracks,
    ) { imported, downloaded ->
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

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            library.toggleFavorite(track, defaultCategory.value)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    /** Removes the supplied play-history rows. */
    fun deleteHistorySelection(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            library.deleteHistory(tracks.map { it.id })
            _message.value = "已删除 ${tracks.size} 条播放记录"
            clearSelection()
        }
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
     * Removes selected imported records and download rows. Imported originals
     * are never deleted; download files follow the download page's policy.
     */
    fun deleteLocalSelection(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val imported = tracks.filter { track ->
            track.sources.any { it.providerId == "local" }
        }
        val downloaded = tracks.filter { track ->
            track.sources.any { it.providerId == "download" }
        }
        viewModelScope.launch {
            if (imported.isNotEmpty()) localMusic.delete(imported.map { it.id })
            if (downloaded.isNotEmpty()) {
                val wanted = downloaded.map { it.id }.toSet()
                downloads.observeTasks().first()
                    .filter { it.track.id in wanted }
                    .forEach { downloads.remove(it.id) }
            }
            _message.value = buildString {
                if (imported.isNotEmpty()) {
                    append("已移除 ${imported.size} 首导入记录（未删除原始文件）")
                }
                if (downloaded.isNotEmpty()) {
                    if (isNotEmpty()) append("；")
                    append("已删除 ${downloaded.size} 首下载歌曲及其文件")
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

    fun selectSourcePreference(trackId: String, providerId: String?) {
        viewModelScope.launch { sourcePreferenceStore.setPreferred(trackId, providerId) }
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
        addFavorites(tracks, setOf(category))
    }

    fun addFavorites(tracks: List<Track>, folders: Set<String>) {
        if (tracks.isEmpty() || folders.isEmpty()) return
        viewModelScope.launch {
            library.addFavorites(tracks, folders)
            _message.value = "已收藏 ${tracks.size} 首到“${folders.joinToString("、")}”"
        }
    }

    fun removeFavoriteFromFolder(tracks: List<Track>, folder: String) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.forEach { library.removeFavoriteFromFolder(it.id, folder) }
            _message.value = "已从“$folder”移除 ${tracks.size} 首"
        }
    }

    /** Removes every membership when [folder] is null, otherwise only that folder. */
    fun removeFavorites(tracks: List<Track>, folder: String?) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            if (folder == null) {
                tracks.forEach { library.removeFavorite(it.id) }
                _message.value = "已取消收藏 ${tracks.size} 首"
            } else {
                tracks.forEach { library.removeFavoriteFromFolder(it.id, folder) }
                _message.value = "已从“$folder”移除 ${tracks.size} 首"
            }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            val defaultCategory = settings.current().defaultFavoriteCategory
            if (name == DEFAULT_FAVORITE_CATEGORY || name == defaultCategory) {
                _message.value = "默认收藏夹不能删除"
                return@launch
            }
            library.deleteCategory(name)
            if (_selectedCategory.value == name) _selectedCategory.value = null
            _message.value = "已删除收藏夹“$name”"
        }
    }

    /** Persists the folder order after a drag operation. */
    fun saveCategoryOrder(order: List<String>) {
        viewModelScope.launch { settings.setFavoriteCategoryOrder(order) }
    }

    /** Removes the download records (and their files) for the selected tracks. */
    fun removeDownloadedSelection(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val wanted = tracks.map { it.id }.toSet()
            downloads.observeTasks().first()
                .filter { it.track.id in wanted }
                .forEach { downloads.remove(it.id) }
            _message.value = "已删除 ${tracks.size} 首已下载歌曲及其文件"
        }
    }

    fun downloadSelected(tracks: List<Track>, qualityKbps: Int? = null) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val queued = downloads.enqueueAll(tracks, qualityKbps = qualityKbps)
            _message.value = "已加入下载队列：$queued 首"
        }
    }

    fun createCategory(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            library.ensureCategory(normalized)
            // New folders are appended to the stored order so they appear at the
            // right end instead of jumping into the alphabetical middle.
            val folders = library.observeFavoriteCategories().first()
            val currentOrder = settings.current().favoriteCategoryOrder
            val previousOrder = orderFavoriteCategories(
                all = folders.filter { it != normalized },
                storedOrder = currentOrder,
            )
            settings.setFavoriteCategoryOrder(previousOrder + normalized)
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
