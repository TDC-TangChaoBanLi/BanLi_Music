package com.banlimusic.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val trackJson: String,
    val addedAt: Long,
    val category: String = "默认收藏",
)

/** Projection used to show which folder a favourite belongs to. */
data class FavoriteFolderRow(
    val trackId: String,
    val category: String,
)

@Entity(tableName = "favorite_categories")
data class FavoriteCategoryEntity(
    @PrimaryKey val name: String,
    val createdAt: Long,
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
    val trackId: String = "",
    val qualityKbps: Int = 0,
    val downloadLyrics: Boolean = false,
    val downloadArtwork: Boolean = false,
)

@Entity(tableName = "local_tracks")
data class LocalTrackEntity(
    @PrimaryKey val trackId: String,
    val trackJson: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val addedAt: Long,
)

/** Index of audio kept in the Media3 playback cache, keyed by a stable app-level cache key. */
@Entity(
    tableName = "cached_tracks",
    indices = [Index("trackId"), Index("lastAccessAt")],
)
data class CachedTrackEntity(
    @PrimaryKey val cacheKey: String,
    val trackId: String,
    val trackJson: String,
    val qualityKbps: Int,
    val totalBytes: Long = 0L,
    val fileExtension: String = "mp3",
    val createdAt: Long,
    val lastAccessAt: Long,
)
