package com.banlimusic.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.banlimusic.app.core.database.entity.CachedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    @Query("SELECT * FROM cached_tracks ORDER BY lastAccessAt DESC")
    fun observeAll(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks ORDER BY lastAccessAt ASC")
    suspend fun getAllOldestFirst(): List<CachedTrackEntity>

    @Query(
        "SELECT * FROM cached_tracks WHERE trackId = :trackId " +
            "AND qualityKbps = :qualityKbps LIMIT 1",
    )
    suspend fun getForTrackAndQuality(trackId: String, qualityKbps: Int): CachedTrackEntity?

    @Upsert
    suspend fun upsert(entity: CachedTrackEntity)

    @Query("DELETE FROM cached_tracks WHERE cacheKey = :cacheKey")
    suspend fun delete(cacheKey: String)

    @Query("DELETE FROM cached_tracks WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    @Query("DELETE FROM cached_tracks")
    suspend fun clear()

    @Query("UPDATE cached_tracks SET totalBytes = :totalBytes WHERE cacheKey = :cacheKey")
    suspend fun updateSize(cacheKey: String, totalBytes: Long)

}
