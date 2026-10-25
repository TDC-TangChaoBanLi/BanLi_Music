package com.banlimusic.desktop.data

import com.banlimusic.app.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Favourites persisted as a small JSON document, independent of Android Room. */
class DesktopLibraryRepository(
    private val file: Path,
    private val json: Json,
) {

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    @Volatile
    private var loadedAtLeastOnce = false

    init {
        load()
    }

    fun isFavorite(trackId: String): Boolean =
        _favorites.value.any { it.id == trackId }

    suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val current = _favorites.value
        val existing = current.any { it.id == track.id }
        val updated = if (existing) {
            current.filterNot { it.id == track.id }
        } else {
            listOf(track) + current
        }
        _favorites.value = updated
        persist()
        !existing
    }

    suspend fun removeFavorite(trackId: String) = withContext(Dispatchers.IO) {
        _favorites.value = _favorites.value.filterNot { it.id == trackId }
        persist()
    }

    suspend fun addFavorite(track: Track) = withContext(Dispatchers.IO) {
        if (_favorites.value.any { it.id == track.id }) return@withContext
        _favorites.value = listOf(track) + _favorites.value
        persist()
    }

    suspend fun recordPlay(track: Track) = withContext(Dispatchers.IO) {
        _history.value = (listOf(track) + _history.value.filterNot { it.id == track.id })
            .take(MAX_HISTORY)
        persist()
    }

    suspend fun clearFavorites() = withContext(Dispatchers.IO) {
        _favorites.value = emptyList()
        persist()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        persist()
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext
        _searchHistory.value = (listOf(trimmed) + _searchHistory.value
            .filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_SEARCH_HISTORY)
        persist()
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        _searchHistory.value = emptyList()
        persist()
    }

    suspend fun clear() = clearFavorites()

    private fun load() {
        if (!Files.exists(file)) {
            loadedAtLeastOnce = true
            return
        }
        runCatching {
            val document = json.decodeFromString(
                DesktopLibraryFile.serializer(),
                Files.readString(file),
            )
            // Newest first; older documents are already in insertion order.
            _favorites.value = document.favorites
                .sortedByDescending { it.addedAt }
                .map { it.track }
                .distinctBy { it.id }
            _history.value = document.recent
                .map { it.track }
                .distinctBy { it.id }
                .take(MAX_HISTORY)
            _searchHistory.value = document.searches.take(MAX_SEARCH_HISTORY)
        }
        loadedAtLeastOnce = true
    }

    private fun persist() {
        if (!loadedAtLeastOnce) return
        runCatching {
            Files.createDirectories(file.parent)
            val document = DesktopLibraryFile(
                favorites = favoriteEntries(),
                recent = recentEntries(),
                searches = _searchHistory.value.take(MAX_SEARCH_HISTORY),
            )
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(
                temp,
                json.encodeToString(DesktopLibraryFile.serializer(), document),
            )
            runCatching {
                Files.move(
                    temp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun favoriteEntries(): List<DesktopFavorite> =
        _favorites.value.mapIndexed { index, track ->
            DesktopFavorite(track = track, addedAt = (_favorites.value.size - index).toLong())
        }

    private fun recentEntries(): List<DesktopFavorite> =
        _history.value.mapIndexed { index, track ->
            DesktopFavorite(track = track, addedAt = (_history.value.size - index).toLong())
        }

    private companion object {
        const val MAX_HISTORY = 50
        const val MAX_SEARCH_HISTORY = 20
    }
}

@Serializable
private data class DesktopLibraryFile(
    val version: Int = 1,
    val favorites: List<DesktopFavorite> = emptyList(),
    /** Added in desktop build 2; older files simply have no recent entries. */
    val recent: List<DesktopFavorite> = emptyList(),
    val searches: List<String> = emptyList(),
)

@Serializable
private data class DesktopFavorite(
    val track: Track,
    val addedAt: Long = 0L,
)
