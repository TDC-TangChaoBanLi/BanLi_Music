package com.banlimusic.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchPage(
    val tracks: List<Track> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
)

@Serializable
data class StreamInfo(
    val url: String,
    val bitrateKbps: Int? = null,
    val sizeBytes: Long? = null,
    val expiresAtEpochMs: Long? = null,
)

@Serializable
data class DownloadInfo(
    val url: String,
    val bitrateKbps: Int? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class Lyrics(
    val raw: String,
    val translated: String? = null,
) {
    val isEmpty: Boolean get() = raw.isBlank()
}

data class ProviderCapabilities(
    val search: Boolean = true,
    val streaming: Boolean = true,
    val lyrics: Boolean = false,
    val download: Boolean = false,
    /**
     * Provider-specific quality options, used for stream resolution and
     * downloads. The app requests one of its four presets and maps it to the
     * closest value here. Empty means quality requests pass through unchanged.
     */
    val downloadQualities: List<Int> = emptyList(),
    val artwork: Boolean = false,
    val playlists: Boolean = false,
)

data class ProviderInfo(
    val id: String,
    val name: String,
    val enabled: Boolean,
)
