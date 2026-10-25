/**
 * 文件作用：收藏、收藏夹、播放历史与搜索历史的数据库访问。
 */
package com.banlimusic.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.banlimusic.app.core.database.entity.FavoriteCategoryEntity
import com.banlimusic.app.core.database.entity.FavoriteMembershipEntity
import com.banlimusic.app.core.database.entity.FavoriteMembershipRow
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query(
        "SELECT t.* FROM favorite_tracks AS t " +
            "WHERE EXISTS(SELECT 1 FROM favorite_memberships AS m " +
            "WHERE m.trackId = t.trackId AND m.deletedAt IS NULL) " +
            "ORDER BY t.addedAt DESC",
    )
    fun observeFavorites(): Flow<List<FavoriteTrackEntity>>

    @Query(
        "SELECT t.* FROM favorite_tracks AS t " +
            "WHERE EXISTS(SELECT 1 FROM favorite_memberships AS m " +
            "WHERE m.trackId = t.trackId AND m.deletedAt IS NULL AND m.folder = :folder) " +
            "ORDER BY t.addedAt DESC",
    )
    fun observeFavoritesByCategory(folder: String): Flow<List<FavoriteTrackEntity>>

    @Query(
        "SELECT name FROM favorite_categories WHERE deletedAt IS NULL " +
            "UNION SELECT folder AS name FROM favorite_memberships WHERE deletedAt IS NULL " +
            "ORDER BY name",
    )
    fun observeFavoriteCategories(): Flow<List<String>>

    @Query(
        "SELECT name FROM favorite_categories WHERE deletedAt IS NULL " +
            "UNION SELECT folder AS name FROM favorite_memberships WHERE deletedAt IS NULL " +
            "ORDER BY name",
    )
    suspend fun getFavoriteCategories(): List<String>

    @Query(
        "SELECT t.* FROM favorite_tracks AS t " +
            "WHERE EXISTS(SELECT 1 FROM favorite_memberships AS m " +
            "WHERE m.trackId = t.trackId AND m.deletedAt IS NULL) " +
            "ORDER BY t.addedAt DESC",
    )
    suspend fun getAllFavorites(): List<FavoriteTrackEntity>

    @Query("SELECT * FROM favorite_tracks")
    suspend fun getAllTracksRaw(): List<FavoriteTrackEntity>

    @Query("SELECT * FROM favorite_memberships")
    suspend fun getAllMembershipsRaw(): List<FavoriteMembershipEntity>

    @Query("SELECT * FROM favorite_categories")
    suspend fun getAllCategoriesRaw(): List<FavoriteCategoryEntity>

    @Upsert
    suspend fun upsertCategory(entity: FavoriteCategoryEntity)

    @Upsert
    suspend fun upsertCategories(entities: List<FavoriteCategoryEntity>)

    @Query("SELECT * FROM favorite_categories WHERE name = :name")
    suspend fun getCategory(name: String): FavoriteCategoryEntity?

    @Query("DELETE FROM favorite_categories")
    suspend fun clearCategories()

    @Query(
        "SELECT t.trackId FROM favorite_tracks AS t " +
            "WHERE EXISTS(SELECT 1 FROM favorite_memberships AS m " +
            "WHERE m.trackId = t.trackId AND m.deletedAt IS NULL)",
    )
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT trackId, folder, addedAt FROM favorite_memberships WHERE deletedAt IS NULL")
    fun observeFavoriteMemberships(): Flow<List<FavoriteMembershipRow>>

    @Query("SELECT trackId, folder, addedAt FROM favorite_memberships WHERE deletedAt IS NULL")
    suspend fun getFavoriteMemberships(): List<FavoriteMembershipRow>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorite_memberships " +
            "WHERE trackId = :trackId AND deletedAt IS NULL)",
    )
    suspend fun isFavorite(trackId: String): Boolean

    @Query("SELECT * FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun getTrack(trackId: String): FavoriteTrackEntity?

    @Query("SELECT * FROM favorite_memberships WHERE trackId = :trackId AND folder = :folder")
    suspend fun getMembership(trackId: String, folder: String): FavoriteMembershipEntity?

    @Query("SELECT * FROM favorite_memberships WHERE trackId = :trackId")
    suspend fun getMembershipsForTrack(trackId: String): List<FavoriteMembershipEntity>

    @Upsert
    suspend fun upsertTrack(entity: FavoriteTrackEntity)

    @Upsert
    suspend fun upsertTracks(entities: List<FavoriteTrackEntity>)

    @Upsert
    suspend fun upsertMembership(entity: FavoriteMembershipEntity)

    @Upsert
    suspend fun upsertMemberships(entities: List<FavoriteMembershipEntity>)

    @Query(
        "UPDATE favorite_memberships SET deletedAt = :deletedAt, updatedAt = :deletedAt " +
            "WHERE trackId = :trackId AND folder = :folder",
    )
    suspend fun tombstoneMembership(trackId: String, folder: String, deletedAt: Long)

    @Query(
        "UPDATE favorite_memberships SET deletedAt = :deletedAt, updatedAt = :deletedAt " +
            "WHERE trackId = :trackId AND deletedAt IS NULL",
    )
    suspend fun tombstoneMembershipsForTrack(trackId: String, deletedAt: Long)

    @Query(
        "UPDATE favorite_memberships SET deletedAt = :deletedAt, updatedAt = :deletedAt " +
            "WHERE folder = :folder AND deletedAt IS NULL",
    )
    suspend fun tombstoneMembershipsInFolder(folder: String, deletedAt: Long)

    @Query(
        "UPDATE favorite_categories SET deletedAt = :deletedAt, updatedAt = :deletedAt " +
            "WHERE name = :name",
    )
    suspend fun tombstoneCategory(name: String, deletedAt: Long)

    @Query("DELETE FROM favorite_tracks")
    suspend fun clearTracks()

    @Query("DELETE FROM favorite_memberships")
    suspend fun clearMemberships()

    @Query("DELETE FROM favorite_memberships WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun pruneMembershipTombstones(before: Long)

    @Query("DELETE FROM favorite_categories WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun pruneFolderTombstones(before: Long)

    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 100): Flow<List<PlayHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: PlayHistoryEntity)

    @Query(
        "UPDATE play_history SET playCount = playCount + 1, lastPlayedAt = :playedAt, " +
            "trackJson = :trackJson WHERE trackId = :trackId",
    )
    suspend fun touchHistory(trackId: String, trackJson: String, playedAt: Long): Int

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM search_history ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeSearchHistory(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
