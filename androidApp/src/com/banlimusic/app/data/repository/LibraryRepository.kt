package com.banlimusic.app.data.repository

import com.banlimusic.app.core.database.dao.LibraryDao
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity
import com.banlimusic.app.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class LibraryRepository(
    private val dao: LibraryDao,
    private val json: Json,
) {
    fun observeFavorites(): Flow<List<Track>> = dao.observeFavorites().map { entities ->
        entities.mapNotNull { decode(it.trackJson) }
    }

    fun observeFavoriteIds(): Flow<Set<String>> = dao.observeFavoriteIds().map { it.toSet() }

    fun observeHistory(limit: Int = 100): Flow<List<Track>> = dao.observeHistory(limit).map { entities ->
        entities.mapNotNull { decode(it.trackJson) }
    }

    fun observeSearchHistory(limit: Int = 20): Flow<List<String>> =
        dao.observeSearchHistory(limit).map { entities -> entities.map { it.query } }

    suspend fun isFavorite(trackId: String): Boolean = dao.isFavorite(trackId)

    /** @return the new favorite state. */
    suspend fun toggleFavorite(track: Track): Boolean {
        val existing = dao.isFavorite(track.id)
        if (existing) {
            dao.deleteFavorite(track.id)
        } else {
            dao.upsertFavorite(
                FavoriteTrackEntity(
                    trackId = track.id,
                    trackJson = json.encodeToString(Track.serializer(), track),
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
        return !existing
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
