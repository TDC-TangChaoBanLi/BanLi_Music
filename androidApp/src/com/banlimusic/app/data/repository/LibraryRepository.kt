/**
 * 文件作用：提供收藏、播放历史、搜索历史以及收藏导入导出能力。
 */
package com.banlimusic.app.data.repository

import androidx.room.withTransaction
import com.banlimusic.app.core.database.MusicDatabase
import com.banlimusic.app.core.database.dao.LibraryDao
import com.banlimusic.app.core.database.entity.FavoriteCategoryEntity
import com.banlimusic.app.core.database.entity.FavoriteMembershipEntity
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity
import com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.core.model.SyncFolder
import com.banlimusic.app.core.model.SyncMembership
import com.banlimusic.app.core.model.SyncTrack
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.sync.FavoriteMerge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FavoritesExport(
    val version: Int = 2,
    val categories: List<String> = emptyList(),
    val favorites: List<FavoriteExportItem> = emptyList(),
)

@Serializable
data class FavoriteExportItem(
    /** v1 field, still written first for older readers and read as a fallback. */
    val category: String = DEFAULT_FAVORITE_CATEGORY,
    val addedAt: Long = 0L,
    val track: Track,
    /** v2: every folder that contains this track. */
    val folders: List<String> = emptyList(),
)

class LibraryRepository(
    private val database: MusicDatabase,
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

    fun observeFavoriteIds(): Flow<Set<String>> = dao.observeFavoriteIds().map { it.toSet() }

    /** Maps application-level track ids to every favourite folder they live in. */
    fun observeFavoriteFolders(): Flow<Map<String, Set<String>>> =
        dao.observeFavoriteMemberships().map { rows ->
            rows.groupBy({ it.trackId }, { it.folder })
                .mapValues { (_, folders) -> folders.toSet() }
        }

    suspend fun ensureCategory(name: String) {
        val normalized = normalizeFolder(name)
        val now = System.currentTimeMillis()
        database.withTransaction { ensureCategoryLocked(normalized, now) }
    }

    private suspend fun ensureCategoryLocked(name: String, now: Long) {
        val existing = dao.getCategory(name)
        dao.upsertCategory(
            FavoriteCategoryEntity(
                name = name,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

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
        val normalized = normalizeFolder(category)
        val now = System.currentTimeMillis()
        val active = dao.getMembershipsForTrack(track.id).filter { it.deletedAt == null }
        database.withTransaction {
            if (active.isEmpty()) {
                ensureCategoryLocked(normalized, now)
                writeFavoriteLocked(track, normalized, now)
            } else {
                dao.tombstoneMembershipsForTrack(track.id, now)
            }
        }
        return active.isEmpty()
    }

    suspend fun addFavorite(track: Track, category: String = DEFAULT_FAVORITE_CATEGORY) {
        addFavorites(listOf(track), setOf(category))
    }

    suspend fun addFavorites(
        tracks: Collection<Track>,
        category: String = DEFAULT_FAVORITE_CATEGORY,
    ) {
        addFavorites(tracks, setOf(category))
    }

    /** Adds the tracks to every selected folder without touching other memberships. */
    suspend fun addFavorites(tracks: Collection<Track>, folders: Set<String>) {
        val normalizedFolders = folders.map(::normalizeFolder)
            .filter { it.isNotBlank() }
            .toSet()
            .ifEmpty { setOf(DEFAULT_FAVORITE_CATEGORY) }
        val now = System.currentTimeMillis()
        val distinctTracks = tracks.distinctBy { it.id }
        database.withTransaction {
            normalizedFolders.forEach { ensureCategoryLocked(it, now) }
            distinctTracks.forEach { track ->
                normalizedFolders.forEach { folder -> writeFavoriteLocked(track, folder, now) }
            }
        }
    }

    /** Replaces the whole folder set of one track, writing tombstones for removals. */
    suspend fun setFavoriteFolders(track: Track, folders: Set<String>) {
        val normalized = folders.map(::normalizeFolder).filter { it.isNotBlank() }.toSet()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val active = dao.getMembershipsForTrack(track.id)
                .filter { it.deletedAt == null }
                .map { it.folder }
                .toSet()
            normalized.forEach { folder ->
                if (folder !in active) {
                    ensureCategoryLocked(folder, now)
                    writeFavoriteLocked(track, folder, now)
                }
            }
            (active - normalized).forEach { folder ->
                dao.tombstoneMembership(track.id, folder, now)
            }
        }
    }

    suspend fun removeFavorite(trackId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction { dao.tombstoneMembershipsForTrack(trackId, now) }
    }

    suspend fun removeFavoriteFromFolder(trackId: String, folder: String) {
        val now = System.currentTimeMillis()
        database.withTransaction { dao.tombstoneMembership(trackId, normalizeFolder(folder), now) }
    }

    suspend fun deleteCategory(name: String) {
        val normalized = normalizeFolder(name)
        if (normalized == DEFAULT_FAVORITE_CATEGORY) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.tombstoneMembershipsInFolder(normalized, now)
            dao.tombstoneCategory(normalized, now)
        }
    }

    private suspend fun writeFavoriteLocked(track: Track, folder: String, now: Long) {
        val existingTrack = dao.getTrack(track.id)
        dao.upsertTrack(
            FavoriteTrackEntity(
                trackId = track.id,
                trackJson = json.encodeToString(Track.serializer(), track),
                addedAt = existingTrack?.addedAt ?: now,
                updatedAt = now,
            ),
        )
        val existingMembership = dao.getMembership(track.id, folder)
        dao.upsertMembership(
            FavoriteMembershipEntity(
                trackId = track.id,
                folder = folder,
                addedAt = existingMembership?.addedAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    private fun normalizeFolder(name: String): String =
        name.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }

    suspend fun exportFavoritesJson(): String {
        val categories = dao.getFavoriteCategories()
        val memberships = dao.getFavoriteMemberships().groupBy { it.trackId }
        val favorites = dao.getAllFavorites().mapNotNull { entity ->
            decode(entity.trackJson)?.let { track ->
                val folders = memberships[entity.trackId].orEmpty()
                    .map { it.folder }
                    .distinct()
                    .sorted()
                FavoriteExportItem(
                    category = folders.firstOrNull() ?: DEFAULT_FAVORITE_CATEGORY,
                    addedAt = entity.addedAt,
                    track = track,
                    folders = folders,
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
        val now = System.currentTimeMillis()
        database.withTransaction {
            if (replace) {
                // Keep tombstones so the removal reaches other devices on the next sync.
                dao.getAllMembershipsRaw()
                    .filter { it.deletedAt == null }
                    .forEach { dao.upsertMembership(it.copy(deletedAt = now, updatedAt = now)) }
                dao.getAllCategoriesRaw()
                    .filter { it.deletedAt == null }
                    .forEach { dao.upsertCategory(it.copy(deletedAt = now, updatedAt = now)) }
            }
            val folderNames = buildList {
                addAll(data.categories.map(::normalizeFolder))
                data.favorites.forEach { item ->
                    val folders = item.folders.map(::normalizeFolder).filter { it.isNotBlank() }
                    if (folders.isEmpty()) {
                        add(normalizeFolder(item.category))
                    } else {
                        addAll(folders)
                    }
                }
            }
            folderNames.distinct().forEach { ensureCategoryLocked(it, now) }
            data.favorites.forEach { item ->
                val folders = item.folders.map(::normalizeFolder).filter { it.isNotBlank() }
                    .ifEmpty { listOf(normalizeFolder(item.category)) }
                val existingTrack = dao.getTrack(item.track.id)
                val addedAt = existingTrack?.addedAt
                    ?: item.addedAt.takeIf { it > 0L }
                    ?: now
                dao.upsertTrack(
                    FavoriteTrackEntity(
                        trackId = item.track.id,
                        trackJson = json.encodeToString(Track.serializer(), item.track),
                        addedAt = addedAt,
                        updatedAt = now,
                    ),
                )
                folders.distinct().forEach { folder ->
                    val existing = dao.getMembership(item.track.id, folder)
                    dao.upsertMembership(
                        FavoriteMembershipEntity(
                            trackId = item.track.id,
                            folder = folder,
                            addedAt = existing?.addedAt ?: addedAt,
                            updatedAt = now,
                            deletedAt = null,
                        ),
                    )
                }
            }
        }
        return data.favorites.size
    }

    /** Full snapshot including tombstones, used by the cloud sync coordinator. */
    suspend fun exportSyncDocument(deviceId: String): FavoriteSyncDocument {
        val tracks = dao.getAllTracksRaw().mapNotNull { entity ->
            decode(entity.trackJson)?.let { track ->
                SyncTrack(
                    trackId = entity.trackId,
                    track = track,
                    addedAt = entity.addedAt,
                    updatedAt = entity.updatedAt,
                )
            }
        }
        val memberships = dao.getAllMembershipsRaw().map { entity ->
            SyncMembership(
                trackId = entity.trackId,
                folder = entity.folder,
                addedAt = entity.addedAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt,
            )
        }
        val folders = dao.getAllCategoriesRaw().map { entity ->
            SyncFolder(
                name = entity.name,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt,
            )
        }
        return FavoriteSyncDocument(
            generatedAt = System.currentTimeMillis(),
            deviceId = deviceId,
            folders = folders,
            tracks = tracks,
            memberships = memberships,
        )
    }

    /** Applies a merged document atomically; deletes are tombstone based. */
    suspend fun applySyncDocument(document: FavoriteSyncDocument) {
        val now = System.currentTimeMillis()
        val cutoff = now - FavoriteMerge.TOMBSTONE_TTL_MS
        database.withTransaction {
            if (document.tracks.isNotEmpty()) {
                dao.upsertTracks(
                    document.tracks.map { record ->
                        FavoriteTrackEntity(
                            trackId = record.trackId,
                            trackJson = json.encodeToString(Track.serializer(), record.track),
                            addedAt = record.addedAt,
                            updatedAt = record.updatedAt,
                        )
                    },
                )
            }
            if (document.memberships.isNotEmpty()) {
                dao.upsertMemberships(
                    document.memberships.map { record ->
                        FavoriteMembershipEntity(
                            trackId = record.trackId,
                            folder = record.folder,
                            addedAt = record.addedAt,
                            updatedAt = record.updatedAt,
                            deletedAt = record.deletedAt,
                        )
                    },
                )
            }
            if (document.folders.isNotEmpty()) {
                dao.upsertCategories(
                    document.folders.map { record ->
                        FavoriteCategoryEntity(
                            name = record.name,
                            createdAt = record.createdAt,
                            updatedAt = record.updatedAt,
                            deletedAt = record.deletedAt,
                        )
                    },
                )
            }
            dao.pruneMembershipTombstones(cutoff)
            dao.pruneFolderTombstones(cutoff)
        }
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

    suspend fun deleteHistory(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        dao.deleteHistory(trackIds.toList())
    }

    suspend fun clearHistory() = dao.clearHistory()

    private fun decode(raw: String): Track? = runCatching {
        json.decodeFromString(Track.serializer(), raw)
    }.getOrNull()
}
