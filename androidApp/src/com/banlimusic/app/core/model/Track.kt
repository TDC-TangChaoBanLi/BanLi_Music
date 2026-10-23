package com.banlimusic.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String? = null,
    val name: String,
)

@Serializable
data class Album(
    val id: String? = null,
    val title: String,
    val artworkUrl: String? = null,
)

@Serializable
enum class TrackAction {
    STREAM,
    DOWNLOAD,
}

/**
 * A track as exposed by one provider. [providerTrackId] must never be treated as
 * the application-wide track id; the two identifiers live at different levels.
 */
@Serializable
data class TrackSource(
    val providerId: String,
    val providerTrackId: String,
    val availableActions: Set<TrackAction> = setOf(TrackAction.STREAM),
    val artworkKey: String? = null,
    val lyricKey: String? = null,
    val qualityKbps: Int? = null,
    val durationMs: Long? = null,
)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val sources: List<TrackSource> = emptyList(),
    val isExplicit: Boolean = false,
) {
    val artistLine: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "未知歌手" }

    val primarySource: TrackSource?
        get() = sources.firstOrNull { TrackAction.STREAM in it.availableActions } ?: sources.firstOrNull()

    fun sourceFor(providerId: String): TrackSource? = sources.firstOrNull { it.providerId == providerId }
}
