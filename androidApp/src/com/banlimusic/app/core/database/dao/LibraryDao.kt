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
import com.banlimusic.app.core.database.entity.FavoriteFolderRow
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteTrackEntity>>

    @Query("SELECT * FROM favorites WHERE category = :category ORDER BY addedAt DESC")
    fun observeFavoritesByCategory(category: String): Flow<List<FavoriteTrackEntity>>

    @Query(
        "SELECT name FROM favorite_categories " +
            "UNION SELECT category AS name FROM favorites ORDER BY name",
    )
    fun observeFavoriteCategories(): Flow<List<String>>

    @Query(
        "SELECT name FROM favorite_categories " +
            "UNION SELECT category AS name FROM favorites ORDER BY name",
    )
    suspend fun getFavoriteCategories(): List<String>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllFavorites(): List<FavoriteTrackEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    @Upsert
    suspend fun upsertCategory(entity: FavoriteCategoryEntity)

    @Query("DELETE FROM favorite_categories")
    suspend fun clearCategories()

    @Query("SELECT trackId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT trackId, category FROM favorites")
    fun observeFavoriteFolders(): Flow<List<FavoriteFolderRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Upsert
    suspend fun upsertFavorite(entity: FavoriteTrackEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

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
