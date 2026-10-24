package com.banlimusic.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.banlimusic.app.core.database.entity.LocalTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMusicDao {

    @Query("SELECT * FROM local_tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocalTrackEntity>>

    @Upsert
    suspend fun upsertAll(entities: List<LocalTrackEntity>)

    @Query("DELETE FROM local_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM local_tracks WHERE trackId IN (:trackIds)")
    suspend fun deleteAll(trackIds: Collection<String>)

    @Query("DELETE FROM local_tracks")
    suspend fun clear()
}
