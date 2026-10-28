package com.banlimusic.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Track
import com.banlimusic.desktop.DesktopContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

enum class DesktopTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Rounded.Home),
    SEARCH("search", "搜索", Icons.Rounded.Search),
    LIBRARY("library", "音乐库", Icons.Rounded.LibraryMusic),
    DOWNLOADS("downloads", "下载", Icons.Rounded.Download),
    SETTINGS("settings", "设置", Icons.Rounded.Settings),
}

data class DesktopSearchState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
)

class DesktopAppState(
    val container: DesktopContainer,
    private val scope: CoroutineScope,
) {
    val settings = container.settingsRepository.settings
    val favorites = container.libraryRepository.favorites
    val history = container.libraryRepository.history
    val searchHistory = container.libraryRepository.searchHistory
    val favoriteCategories = container.libraryRepository.favoriteCategories
    val favoriteFolders = container.libraryRepository.favoriteFolders
    val localTracks = container.libraryRepository.localTracks
    val downloads = container.downloads.tasks
    val playback = container.player.state

    var localScanning by mutableStateOf(false)
        private set
    var localScanError by mutableStateOf<String?>(null)
        private set

    var tab by mutableStateOf(
        DesktopTab.entries.firstOrNull { it.id == settings.value.defaultNavItem }
            ?: DesktopTab.HOME,
    )
    var showPlayer by mutableStateOf(false)
        private set
    var search by mutableStateOf(DesktopSearchState())
        private set

    private var searchJob: Job? = null

    fun selectTab(next: DesktopTab) {
        tab = next
        showPlayer = false
    }

    fun openSearch() {
        tab = DesktopTab.SEARCH
        showPlayer = false
    }

    fun openPlayer() {
        if (playback.value.track != null) showPlayer = true
    }

    fun closePlayer() {
        showPlayer = false
    }

    fun updateQuery(value: String) {
        search = search.copy(query = value)
    }

    fun submitSearch() {
        val query = search.query.trim()
        if (query.isEmpty()) return
        scope.launch {
            try {
                container.libraryRepository.recordSearch(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Search history is optional.
            }
        }
        searchJob?.cancel()
        search = search.copy(
            submittedQuery = query,
            loading = true,
            loadingMore = false,
            error = null,
            results = emptyList(),
            page = 0,
            hasMore = false,
        )
        launchSearch(query, page = 1, append = false)
    }

    fun loadMore() {
        if (search.loading || search.loadingMore || !search.hasMore) return
        val query = search.submittedQuery
        if (query.isBlank()) return
        search = search.copy(loadingMore = true, error = null)
        launchSearch(query, page = search.page + 1, append = true)
    }

    fun retrySearch() {
        if (search.submittedQuery.isNotBlank()) submitSearch()
    }

    fun useSearchHistory(query: String) {
        search = search.copy(query = query)
        submitSearch()
    }

    fun clearSearchHistory() {
        scope.launch {
            try {
                container.libraryRepository.clearSearchHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Nothing user-visible depends on this cleanup succeeding.
            }
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch {
            try {
                container.libraryRepository.toggleFavorite(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The next tap retries; a failed write must not crash the UI.
            }
        }
    }

    fun download(track: Track, qualityKbps: Int = settings.value.downloadQualityKbps) {
        container.downloads.download(track, qualityKbps)
    }

    /** Imports a portable download manifest and queues missing songs. */
    fun importManifest(file: File) {
        scope.launch {
            try {
                val text = file.readText()
                val manifest = container.json.decodeFromString(
                    com.banlimusic.app.core.model.DownloadManifest.serializer(),
                    text,
                )
                manifest.entries.forEach { entry ->
                    container.downloads.download(
                        entry.track,
                        entry.qualityKbps.takeIf { it > 0 }
                            ?: settings.value.downloadQualityKbps,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                localScanError = error.userMessage()
            }
        }
    }

    fun downloadTracks(tracks: Collection<Track>, qualityKbps: Int) {
        tracks.forEach { container.downloads.download(it, qualityKbps) }
    }

    fun addTracksToCategory(tracks: Collection<Track>, category: String) {
        addTracksToCategories(tracks, setOf(category))
    }

    fun addTracksToCategories(tracks: Collection<Track>, categories: Set<String>) {
        scope.launch {
            try {
                container.libraryRepository.addFavorites(tracks, categories)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The list remains unchanged; the user can retry.
            }
        }
    }

    fun removeFavorites(tracks: Collection<Track>, category: String?) {
        scope.launch {
            try {
                container.libraryRepository.removeFavorites(tracks, category)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the list as-is so the user can retry.
            }
        }
    }

    fun deleteFavoriteCategory(name: String) {
        scope.launch {
            try {
                container.libraryRepository.deleteCategory(name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the folder so the user does not lose data silently.
            }
        }
    }

    fun saveFavoriteCategoryOrder(categories: List<String>) {
        scope.launch {
            try {
                container.libraryRepository.saveCategoryOrder(categories)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Ordering is cosmetic; ignore failures.
            }
        }
    }

    fun createFavoriteCategory(name: String, onCreated: (String) -> Unit) {
        scope.launch {
            try {
                onCreated(container.libraryRepository.createCategory(name))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the dialog open so the user can retry.
            }
        }
    }

    fun scanLocalMusic(directory: String) {
        if (localScanning) return
        localScanning = true
        localScanError = null
        scope.launch {
            try {
                container.libraryRepository.scanLocal(directory)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                localScanError = error.userMessage()
            } finally {
                localScanning = false
            }
        }
    }

    fun importLocalFiles(files: List<File>) {
        if (files.isEmpty()) return
        scope.launch {
            try {
                container.libraryRepository.importLocalFiles(files.map { it.toPath() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                localScanError = error.userMessage()
            }
        }
    }

    fun clearHistory() {
        scope.launch {
            try {
                container.libraryRepository.clearHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the list; the user can retry.
            }
        }
    }

    fun removeHistory(tracks: Collection<Track>) {
        scope.launch {
            try {
                container.libraryRepository.removeHistory(tracks)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the list; the user can retry.
            }
        }
    }

    fun removeLocalTracks(tracks: Collection<Track>) {
        scope.launch {
            try {
                tracks.forEach { container.libraryRepository.removeLocal(it.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the list; the user can retry.
            }
        }
    }

    fun removeDownloaded(tracks: Collection<Track>) {
        tracks.forEach { container.downloads.remove(it.id) }
    }

    fun clearLocalMusic() {
        scope.launch {
            try {
                container.libraryRepository.clearLocal()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Nothing user-visible depends on this cleanup succeeding.
            }
        }
    }

    fun playLocal(track: Track, file: File) {
        container.player.playLocal(track, file)
        recordPlay(track)
    }

    fun play(track: Track, queue: List<Track> = search.results) {
        val localFiles = queue.mapNotNull { queued ->
            localFileOf(queued)?.let { file -> queued.id to file }
        }.toMap()
        if (localFiles.containsKey(track.id)) {
            container.player.playLocalQueue(track, queue, localFiles)
        } else {
            container.player.play(track, queue)
        }
        recordPlay(track)
        if (settings.value.openPlayerOnTrackClick) showPlayer = true
    }

    private fun localFileOf(track: Track): File? = track.sources
        .firstNotNullOfOrNull { source -> source.localUri }
        ?.let { uri -> runCatching { File(URI(uri)) }.getOrNull() }
        ?.takeIf { it.isFile }

    private fun recordPlay(track: Track) {
        scope.launch {
            try {
                container.libraryRepository.recordPlay(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Playback should not fail because history persistence did.
            }
        }
    }

    private fun launchSearch(query: String, page: Int, append: Boolean) {
        searchJob = scope.launch {
            try {
                val pageSize = settings.value.searchPageSize
                val result = container.musicRepository.search(query, page, pageSize)
                val merged = if (append) {
                    (search.results + result.tracks).distinctBy { it.id }
                } else {
                    result.tracks.distinctBy { it.id }
                }
                search = search.copy(
                    results = merged,
                    loading = false,
                    loadingMore = false,
                    error = null,
                    page = page,
                    hasMore = result.hasMore,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                search = search.copy(
                    loading = false,
                    loadingMore = false,
                    error = error.userMessage(),
                )
            }
        }
    }
}
