package com.banlimusic.app.core.model

import kotlinx.serialization.Serializable

/**
 * One document holds the whole favourite library. It is small enough for a single
 * Graph upload (well below the 250 MB simple-upload limit), so no upload session
 * or delta tracking is required.
 */
@Serializable
data class FavoriteSyncDocument(
    val schemaVersion: Int = SYNC_SCHEMA_VERSION,
    val generatedAt: Long = 0L,
    val deviceId: String = "",
    val folders: List<SyncFolder> = emptyList(),
    val tracks: List<SyncTrack> = emptyList(),
    val memberships: List<SyncMembership> = emptyList(),
)

@Serializable
data class SyncFolder(
    val name: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Serializable
data class SyncTrack(
    val trackId: String,
    val track: Track,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A track can belong to several folders, so the membership itself is the synced
 * entity. Deleting a membership leaves a tombstone so the removal propagates to
 * other devices instead of being resurrected by the next pull.
 */
@Serializable
data class SyncMembership(
    val trackId: String,
    val folder: String,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

const val SYNC_SCHEMA_VERSION = 1

/** Cloud file name inside the OneDrive app folder (`Apps/<app name>`). */
const val SYNC_FILE_NAME = "library.json"
