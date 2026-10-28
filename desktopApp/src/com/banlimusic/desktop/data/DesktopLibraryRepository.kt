package com.banlimusic.desktop.data

import com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
import com.banlimusic.app.core.model.Album
import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
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
import java.security.MessageDigest
import java.util.Locale

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

    private val _favoriteCategories = MutableStateFlow(listOf(DEFAULT_FAVORITE_CATEGORY))
    val favoriteCategories: StateFlow<List<String>> = _favoriteCategories.asStateFlow()

    private val _favoriteFolders = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val favoriteFolders: StateFlow<Map<String, Set<String>>> = _favoriteFolders.asStateFlow()

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    /** Wall-clock add times used by the portable backup and cloud sync. */
    private val _favoriteAddedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    @Volatile
    private var loadedAtLeastOnce = false

    init {
        load()
    }

    fun isFavorite(trackId: String): Boolean =
        _favorites.value.any { it.id == trackId }

    suspend fun toggleFavorite(
        track: Track,
        category: String = DEFAULT_FAVORITE_CATEGORY,
    ): Boolean = withContext(Dispatchers.IO) {
        val current = _favorites.value
        val existing = current.any { it.id == track.id }
        if (existing) {
            _favorites.value = current.filterNot { it.id == track.id }
            _favoriteFolders.value = _favoriteFolders.value - track.id
        } else {
            val normalized = normalizeCategory(category)
            ensureCategoryLocked(normalized)
            _favorites.value = listOf(track) + current
            _favoriteFolders.value = _favoriteFolders.value + (track.id to setOf(normalized))
        }
        persist()
        !existing
    }

    suspend fun removeFavorite(trackId: String) = withContext(Dispatchers.IO) {
        _favorites.value = _favorites.value.filterNot { it.id == trackId }
        _favoriteFolders.value = _favoriteFolders.value - trackId
        persist()
    }

    suspend fun addFavorite(
        track: Track,
        category: String = DEFAULT_FAVORITE_CATEGORY,
    ) = withContext(Dispatchers.IO) {
        val normalized = normalizeCategory(category)
        ensureCategoryLocked(normalized)
        if (_favorites.value.none { it.id == track.id }) {
            _favorites.value = listOf(track) + _favorites.value
        }
        _favoriteFolders.value = _favoriteFolders.value + (
            track.id to (_favoriteFolders.value[track.id].orEmpty() + normalized)
            )
        persist()
    }

    suspend fun addFavorites(
        tracks: Collection<Track>,
        category: String = DEFAULT_FAVORITE_CATEGORY,
    ) = addFavorites(tracks, setOf(category))

    suspend fun addFavorites(
        tracks: Collection<Track>,
        categories: Set<String>,
    ) = withContext(Dispatchers.IO) {
        val normalized = categories.map(::normalizeCategory).toSet()
            .ifEmpty { setOf(DEFAULT_FAVORITE_CATEGORY) }
        normalized.forEach(::ensureCategoryLocked)
        val incoming = tracks.distinctBy { it.id }
        if (incoming.isEmpty()) return@withContext
        _favorites.value = (incoming + _favorites.value).distinctBy { it.id }
        val folders = _favoriteFolders.value.toMutableMap()
        incoming.forEach { track ->
            folders[track.id] = folders[track.id].orEmpty() + normalized
        }
        _favoriteFolders.value = folders
        persist()
    }

    suspend fun removeFavorites(tracks: Collection<Track>, category: String?) =
        withContext(Dispatchers.IO) {
            val ids = tracks.map { it.id }.toSet()
            if (category == null) {
                _favorites.value = _favorites.value.filterNot { it.id in ids }
                _favoriteFolders.value = _favoriteFolders.value - ids
            } else {
                val normalized = normalizeCategory(category)
                val folders = _favoriteFolders.value.toMutableMap()
                ids.forEach { id ->
                    val remaining = folders[id].orEmpty() - normalized
                    if (remaining.isEmpty()) folders.remove(id) else folders[id] = remaining
                }
                _favoriteFolders.value = folders
                _favorites.value = _favorites.value.filter { it.id in folders }
            }
            persist()
        }

    suspend fun deleteCategory(name: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeCategory(name)
        _favoriteCategories.value = _favoriteCategories.value - normalized
        val folders = _favoriteFolders.value.mapValues { (_, categories) ->
            categories - normalized
        }.filterValues { it.isNotEmpty() }
        _favoriteFolders.value = folders
        _favorites.value = _favorites.value.filter { it.id in folders }
        persist()
    }

    suspend fun saveCategoryOrder(categories: List<String>) = withContext(Dispatchers.IO) {
        val normalized = categories.map(::normalizeCategory).distinct()
        val known = _favoriteCategories.value
        _favoriteCategories.value = normalized.filter { it in known } +
            known.filterNot { it in normalized }
        persist()
    }

    suspend fun createCategory(name: String): String = withContext(Dispatchers.IO) {
        val normalized = normalizeCategory(name)
        ensureCategoryLocked(normalized)
        persist()
        normalized
    }

    /** Adds individual audio files to the local index without copying them. */
    suspend fun importLocalFiles(paths: Collection<Path>): Int = withContext(Dispatchers.IO) {
        val imported = paths.mapNotNull { path ->
            path.takeIf { Files.isRegularFile(it) && isAudioFile(it) }
                ?.let(::toLocalTrack)
        }
        if (imported.isEmpty()) return@withContext 0
        _localTracks.value = (imported + _localTracks.value).distinctBy { it.id }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
        persist()
        imported.size
    }

    suspend fun removeLocal(trackId: String) = withContext(Dispatchers.IO) {
        _localTracks.value = _localTracks.value.filterNot { it.id == trackId }
        persist()
    }

    suspend fun clearLocal() = withContext(Dispatchers.IO) {
        _localTracks.value = emptyList()
        persist()
    }

    /** Scans [directory] recursively and replaces the desktop local-music index. */
    suspend fun scanLocal(directory: String): Int = withContext(Dispatchers.IO) {
        val root = Path.of(directory)
        require(Files.isDirectory(root)) { "本地音乐目录不存在" }
        val tracks = mutableListOf<Track>()
        val stream = Files.walk(root)
        try {
            val iterator = stream.iterator()
            while (iterator.hasNext() && tracks.size < MAX_LOCAL_TRACKS) {
                val path = iterator.next()
                if (Files.isRegularFile(path) && isAudioFile(path)) {
                    toLocalTrack(path)?.let(tracks::add)
                }
            }
        } finally {
            stream.close()
        }
        val unique = tracks.distinctBy { it.id }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
        _localTracks.value = unique
        persist()
        unique.size
    }

    suspend fun recordPlay(track: Track) = withContext(Dispatchers.IO) {
        _history.value = (listOf(track) + _history.value.filterNot { it.id == track.id })
            .take(MAX_HISTORY)
        persist()
    }

    suspend fun clearFavorites() = withContext(Dispatchers.IO) {
        _favorites.value = emptyList()
        _favoriteFolders.value = emptyMap()
        persist()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        persist()
    }

    suspend fun removeHistory(tracks: Collection<Track>) = withContext(Dispatchers.IO) {
        val ids = tracks.map { it.id }.toSet()
        _history.value = _history.value.filterNot { it.id in ids }
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

    /** Portable backup that is interchangeable with the Android export file. */
    suspend fun exportFavoritesJson(): String = withContext(Dispatchers.IO) {
        val folders = _favoriteFolders.value
        val export = DesktopFavoritesExport(
            categories = _favoriteCategories.value,
            favorites = _favorites.value.map { track ->
                val trackFolders = folders[track.id].orEmpty()
                    .ifEmpty { setOf(DEFAULT_FAVORITE_CATEGORY) }
                    .sorted()
                DesktopFavoriteExport(
                    category = trackFolders.first(),
                    addedAt = _favoriteAddedAt.value[track.id] ?: 0L,
                    track = track,
                    folders = trackFolders,
                )
            },
        )
        json.encodeToString(DesktopFavoritesExport.serializer(), export)
    }

    /** Imports an Android or desktop favourites backup; returns the count. */
    suspend fun importFavoritesJson(raw: String, replace: Boolean): Int =
        withContext(Dispatchers.IO) {
            val export = runCatching {
                json.decodeFromString(DesktopFavoritesExport.serializer(), raw)
            }.getOrNull() ?: return@withContext 0
            if (replace) {
                _favorites.value = emptyList()
                _favoriteFolders.value = emptyMap()
                _favoriteAddedAt.value = emptyMap()
            }
            val now = System.currentTimeMillis()
            val ids = _favorites.value.mapTo(mutableSetOf()) { it.id }
            val folders = _favoriteFolders.value.toMutableMap()
            val addedAt = _favoriteAddedAt.value.toMutableMap()
            val importedTracks = mutableListOf<Track>()
            export.favorites.forEachIndexed { index, item ->
                val trackFolders = item.folders
                    .ifEmpty { listOf(item.category) }
                    .map(::normalizeCategory)
                    .filter { it.isNotBlank() }
                    .toSet()
                    .ifEmpty { setOf(DEFAULT_FAVORITE_CATEGORY) }
                trackFolders.forEach(::ensureCategoryLocked)
                folders[item.track.id] = folders[item.track.id].orEmpty() + trackFolders
                addedAt[item.track.id] = item.addedAt.takeIf { it > 0L }
                    ?: (now - index)
                if (ids.add(item.track.id)) {
                    importedTracks += item.track
                } else {
                    _favorites.value = _favorites.value.map {
                        if (it.id == item.track.id) item.track else it
                    }
                }
            }
            _favoriteCategories.value = (
                listOf(DEFAULT_FAVORITE_CATEGORY) +
                    export.categories +
                    folders.values.flatten()
                ).map(::normalizeCategory).filter { it.isNotBlank() }.distinct()
            _favoriteFolders.value = folders
            _favoriteAddedAt.value = addedAt
            _favorites.value = (importedTracks + _favorites.value).distinctBy { it.id }
            persist()
            export.favorites.size
        }

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
            val favorites = document.favorites
                .sortedByDescending { it.addedAt }
                .distinctBy { it.track.id }
            _favorites.value = favorites.map { it.track }
            _favoriteFolders.value = favorites.associate { favorite ->
                favorite.track.id to (
                    favorite.categories.ifEmpty { listOf(favorite.category) }
                        .map(::normalizeCategory).toSet()
                    )
            }
            _favoriteAddedAt.value = favorites.associate { favorite ->
                favorite.track.id to favorite.addedAt
            }
            _favoriteCategories.value = (
                listOf(DEFAULT_FAVORITE_CATEGORY) +
                    document.categories +
                    _favoriteFolders.value.values.flatten()
                ).filter { it.isNotBlank() }.distinct()
            _history.value = document.recent
                .map { it.track }
                .distinctBy { it.id }
                .take(MAX_HISTORY)
            _searchHistory.value = document.searches.take(MAX_SEARCH_HISTORY)
            _localTracks.value = document.local.distinctBy { it.id }
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
                categories = _favoriteCategories.value,
                local = _localTracks.value,
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

    private fun favoriteEntries(): List<DesktopFavorite> {
        val folders = _favoriteFolders.value
        return _favorites.value.mapIndexed { index, track ->
            val categories = folders[track.id].orEmpty()
                .ifEmpty { setOf(DEFAULT_FAVORITE_CATEGORY) }
            DesktopFavorite(
                track = track,
                addedAt = _favoriteAddedAt.value[track.id]
                    ?: (_favorites.value.size - index).toLong(),
                category = categories.first(),
                categories = categories.toList(),
            )
        }
    }

    private fun normalizeCategory(value: String): String =
        value.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }

    private fun ensureCategoryLocked(category: String) {
        if (_favoriteCategories.value.none { it == category }) {
            _favoriteCategories.value = _favoriteCategories.value + category
        }
    }

    private fun isAudioFile(path: Path): Boolean {
        val extension = path.fileName?.toString()
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?: return false
        return extension in AUDIO_EXTENSIONS
    }

    private fun toLocalTrack(path: Path): Track? {
        val fileName = path.fileName?.toString() ?: return null
        val baseName = fileName.substringBeforeLast('.')
        val split = baseName.split(" - ", limit = 2)
        val artist = split.getOrNull(0)?.trim().orEmpty().ifBlank { "未知歌手" }
        val title = split.getOrNull(1)?.trim().orEmpty().ifBlank { baseName }
        val parent = path.parent
        val albumTitle = parent?.fileName?.toString()?.takeIf { it.isNotBlank() }
        val artwork = listOf("cover.jpg", "folder.jpg", "album.jpg", "front.jpg")
            .map { name -> parent?.resolve(name) }
            .firstOrNull { it != null && Files.isRegularFile(it) }
            ?.toUri()
            ?.toString()
        return Track(
            id = "local:${sha1(path.toAbsolutePath().toString())}",
            title = title,
            artists = listOf(Artist(name = artist)),
            album = albumTitle?.let { Album(title = it, artworkUrl = artwork) },
            artworkUrl = artwork,
            sources = listOf(
                TrackSource(
                    providerId = "local",
                    providerTrackId = path.toAbsolutePath().toString(),
                    availableActions = setOf(TrackAction.STREAM),
                    localUri = path.toUri().toString(),
                ),
            ),
        )
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun recentEntries(): List<DesktopFavorite> =
        _history.value.mapIndexed { index, track ->
            DesktopFavorite(track = track, addedAt = (_history.value.size - index).toLong())
        }

    private companion object {
        const val MAX_HISTORY = 50
        const val MAX_SEARCH_HISTORY = 20
        const val MAX_LOCAL_TRACKS = 5_000
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma",
        )
    }
}

@Serializable
private data class DesktopLibraryFile(
    val version: Int = 1,
    val favorites: List<DesktopFavorite> = emptyList(),
    /** Added in desktop build 2; older files simply have no recent entries. */
    val recent: List<DesktopFavorite> = emptyList(),
    val searches: List<String> = emptyList(),
    val categories: List<String> = listOf(DEFAULT_FAVORITE_CATEGORY),
    val local: List<Track> = emptyList(),
)

@Serializable
private data class DesktopFavoritesExport(
    val version: Int = 2,
    val categories: List<String> = emptyList(),
    val favorites: List<DesktopFavoriteExport> = emptyList(),
)

@Serializable
private data class DesktopFavoriteExport(
    val category: String = DEFAULT_FAVORITE_CATEGORY,
    val addedAt: Long = 0L,
    val track: Track,
    val folders: List<String> = emptyList(),
)

@Serializable
private data class DesktopFavorite(
    val track: Track,
    val addedAt: Long = 0L,
    val category: String = DEFAULT_FAVORITE_CATEGORY,
    /** Multiple folders per track, added in desktop build 3. */
    val categories: List<String> = emptyList(),
)
