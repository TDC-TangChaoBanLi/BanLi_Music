package com.banlimusic.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val trackJson: String,
    val addedAt: Long,
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val trackId: String,
    val trackJson: String,
    val lastPlayedAt: Long,
    val playCount: Int,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedAt: Long,
)

@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt")],
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val trackJson: String,
    val title: String,
    val artist: String,
    val providerId: String,
    val providerTrackId: String,
    val status: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val error: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
