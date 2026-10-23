package com.banlimusic.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Upsert
    suspend fun upsert(entity: DownloadTaskEntity)

    @Query(
        "UPDATE downloads SET status = :status, error = :error, filePath = :filePath, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        error: String?,
        filePath: String?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE downloads SET bytesDownloaded = :bytes, totalBytes = :total, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProgress(id: String, bytes: Long, total: Long, updatedAt: Long)

    @Query("UPDATE downloads SET retryCount = :retryCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRetryCount(id: String, retryCount: Int, updatedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompleted(): List<DownloadTaskEntity>

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun completedBytes(): Long
}
