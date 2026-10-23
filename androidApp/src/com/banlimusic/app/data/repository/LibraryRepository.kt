package com.banlimusic.app.data.repository

import com.banlimusic.app.core.database.dao.LibraryDao
import com.banlimusic.app.core.database.entity.FavoriteCategoryEntity
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity
import com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
import com.banlimusic.app.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FavoritesExport(
    val version: Int = 1,
    val categories: List<String> = emptyList(),
    val favorites: List<FavoriteExportItem> = emptyList(),
)

@Serializable
data class FavoriteExportItem(
    val category: String = DEFAULT_FAVORITE_CATEGORY,
    val addedAt: Long = 0L,
    val track: Track,
)

class LibraryRepository(
    private val dao: LibraryDao,
    private val json: Json,
) {
    fun observeFavorites(category: String? = null): Flow<List<Track>> {
        val source = if (category == null) {
            dao.observeFavorites()
        } else {
            dao.observeFavoritesByCategory(category)
        }
        return source.map { entities -> entities.mapNotNull { decode(it.trackJson) } }
    }

    fun observeFavoriteCategories(): Flow<List<String>> = dao.observeFavoriteCategories()

    suspend fun ensureCategory(name: String) {
        val normalized = name.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }
        dao.upsertCategory(
            FavoriteCategoryEntity(normalized, System.currentTimeMillis()),
        )
    }

    fun observeFavoriteIds(): Flow<Set<String>> = dao.observeFavoriteIds().map { it.toSet() }

    fun observeHistory(limit: Int = 100): Flow<List<Track>> = dao.observeHistory(limit).map { entities ->
        entities.mapNotNull { decode(it.trackJson) }
    }

    fun observeSearchHistory(limit: Int = 20): Flow<List<String>> =
        dao.observeSearchHistory(limit).map { entities -> entities.map { it.query } }

    suspend fun isFavorite(trackId: String): Boolean = dao.isFavorite(trackId)

    /** @return the new favorite state. */
    suspend fun toggleFavorite(
        track: Track,
        category: String = DEFAULT_FAVORITE_CATEGORY,
    ): Boolean {
        val existing = dao.isFavorite(track.id)
        if (existing) {
            dao.deleteFavorite(track.id)
        } else {
            addFavorite(track, category)
        }
        return !existing
    }

    suspend fun addFavorite(track: Track, category: String = DEFAULT_FAVORITE_CATEGORY) {
        val normalized = category.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }
        ensureCategory(normalized)
        dao.upsertFavorite(
            FavoriteTrackEntity(
                trackId = track.id,
                trackJson = json.encodeToString(Track.serializer(), track),
                addedAt = System.currentTimeMillis(),
                category = normalized,
            ),
        )
    }

    suspend fun addFavorites(
        tracks: Collection<Track>,
        category: String = DEFAULT_FAVORITE_CATEGORY,
    ) {
        val normalized = category.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }
        ensureCategory(normalized)
        val now = System.currentTimeMillis()
        tracks.distinctBy { it.id }.forEach { track ->
            dao.upsertFavorite(
                FavoriteTrackEntity(
                    trackId = track.id,
                    trackJson = json.encodeToString(Track.serializer(), track),
                    addedAt = now,
                    category = normalized,
                ),
            )
        }
    }

    suspend fun removeFavorite(trackId: String) = dao.deleteFavorite(trackId)

    suspend fun exportFavoritesJson(): String {
        val categories = dao.getFavoriteCategories()
        val favorites = dao.getAllFavorites().mapNotNull { entity ->
            decode(entity.trackJson)?.let { track ->
                FavoriteExportItem(
                    category = entity.category,
                    addedAt = entity.addedAt,
                    track = track,
                )
            }
        }
        return json.encodeToString(
            FavoritesExport.serializer(),
            FavoritesExport(categories = categories, favorites = favorites),
        )
    }

    /** @return number of imported favorite entries. */
    suspend fun importFavoritesJson(raw: String, replace: Boolean): Int {
        val data = json.decodeFromString(FavoritesExport.serializer(), raw)
        if (replace) {
            dao.clearFavorites()
            dao.clearCategories()
        }
        (data.categories + data.favorites.map { it.category }).forEach { category ->
            ensureCategory(category)
        }
        val now = System.currentTimeMillis()
        data.favorites.forEach { item ->
            val category = item.category.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }
            dao.upsertFavorite(
                FavoriteTrackEntity(
                    trackId = item.track.id,
                    trackJson = json.encodeToString(Track.serializer(), item.track),
                    addedAt = item.addedAt.takeIf { it > 0L } ?: now,
                    category = category,
                ),
            )
        }
        return data.favorites.size
    }

    suspend fun recordPlay(track: Track) {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString(Track.serializer(), track)
        if (dao.touchHistory(track.id, encoded, now) == 0) {
            dao.insertHistory(
                PlayHistoryEntity(
                    trackId = track.id,
                    trackJson = encoded,
                    lastPlayedAt = now,
                    playCount = 1,
                ),
            )
        }
    }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dao.insertSearchHistory(SearchHistoryEntity(trimmed, System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() = dao.clearSearchHistory()

    suspend fun clearHistory() = dao.clearHistory()

    private fun decode(raw: String): Track? = runCatching {
        json.decodeFromString(Track.serializer(), raw)
    }.getOrNull()
}
