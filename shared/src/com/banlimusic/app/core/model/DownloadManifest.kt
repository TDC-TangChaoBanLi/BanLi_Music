package com.banlimusic.app.core.model

import kotlinx.serialization.Serializable

/**
 * Portable description of one downloaded song. It travels inside the download
 * directory so another device can rebuild the track, its provider sources and
 * the optional lyric/artwork sidecars without the original database.
 */
@Serializable
data class DownloadManifestEntry(
    val trackId: String = "",
    val fileName: String = "",
    val track: Track = Track(id = "", title = ""),
    val qualityKbps: Int = 0,
    val downloadedAt: Long = 0L,
    val sizeBytes: Long = 0L,
    /** Raw LRC text so lyrics survive without a provider request. */
    val lyricsText: String? = null,
    /** File name of the artwork sidecar next to the audio file. */
    val artworkFileName: String? = null,
)

@Serializable
data class DownloadManifest(
    val version: Int = CURRENT_VERSION,
    val entries: List<DownloadManifestEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Pure merge helpers. The manifest is keyed by track id plus quality so the
 * same song can exist in more than one quality without overwriting metadata.
 */
object DownloadManifestOps {

    const val FILE_NAME = "banli-downloads.json"

    fun keyOf(entry: DownloadManifestEntry): String =
        "${entry.trackId}@${entry.qualityKbps}"

    fun upsert(
        manifest: DownloadManifest,
        entry: DownloadManifestEntry,
    ): DownloadManifest {
        val key = keyOf(entry)
        val merged = manifest.entries.filterNot { keyOf(it) == key } + entry
        return manifest.copy(entries = merged)
    }

    fun remove(
        manifest: DownloadManifest,
        trackId: String,
        qualityKbps: Int? = null,
    ): DownloadManifest {
        val remaining = manifest.entries.filterNot { entry ->
            entry.trackId == trackId && (qualityKbps == null || entry.qualityKbps == qualityKbps)
        }
        return manifest.copy(entries = remaining)
    }

    /** Entries from [incoming] win on conflict; existing order is preserved otherwise. */
    fun merge(
        base: DownloadManifest,
        incoming: DownloadManifest,
    ): DownloadManifest {
        if (incoming.entries.isEmpty()) return base
        val incomingByKey = incoming.entries.associateBy(::keyOf)
        val baseKeys = base.entries.mapTo(HashSet(), ::keyOf)
        val merged = base.entries.map { entry -> incomingByKey[keyOf(entry)] ?: entry } +
            incoming.entries.filter { keyOf(it) !in baseKeys }
        return base.copy(entries = merged)
    }
}
