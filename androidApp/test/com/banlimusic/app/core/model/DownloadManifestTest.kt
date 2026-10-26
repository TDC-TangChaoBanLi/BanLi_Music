package com.banlimusic.app.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadManifestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun entry(
        trackId: String,
        quality: Int = 128,
        fileName: String = "$trackId.mp3",
    ): DownloadManifestEntry = DownloadManifestEntry(
        trackId = trackId,
        fileName = fileName,
        track = Track(id = trackId, title = "Title $trackId"),
        qualityKbps = quality,
        downloadedAt = 1_000L,
        sizeBytes = 2_048L,
        lyricsText = "[00:00]lyric",
    )

    @Test
    fun roundTripsThroughJson() {
        val manifest = DownloadManifest(entries = listOf(entry("a"), entry("b")))

        val decoded = json.decodeFromString(
            DownloadManifest.serializer(),
            json.encodeToString(DownloadManifest.serializer(), manifest),
        )

        assertEquals(manifest, decoded)
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val decoded = json.decodeFromString(
            DownloadManifest.serializer(),
            """{"version":1,"entries":[{"trackId":"a","fileName":"a.mp3",""" +
                """"track":{"id":"a","title":"T"}}]}""",
        )

        assertEquals(1, decoded.version)
        assertEquals(0, decoded.entries.single().qualityKbps)
        assertEquals(null, decoded.entries.single().lyricsText)
        assertEquals("T", decoded.entries.single().track.title)
    }

    @Test
    fun upsertReplacesSameTrackAndQuality() {
        val base = DownloadManifest(entries = listOf(entry("a"), entry("b")))

        val updated = DownloadManifestOps.upsert(
            base,
            entry("a", fileName = "a-new.mp3"),
        )

        assertEquals(2, updated.entries.size)
        assertEquals("a-new.mp3", updated.entries.last().fileName)
        assertEquals(listOf("b", "a"), updated.entries.map { it.trackId })
    }

    @Test
    fun removeTargetsOneQualityOrEveryQuality() {
        val base = DownloadManifest(
            entries = listOf(entry("a", 128), entry("a", 320), entry("b")),
        )

        val oneQuality = DownloadManifestOps.remove(base, "a", 128)
        assertEquals(2, oneQuality.entries.size)
        assertEquals(listOf(320, 128), oneQuality.entries.map { it.qualityKbps })

        val everyQuality = DownloadManifestOps.remove(base, "a")
        assertEquals(1, everyQuality.entries.size)
        assertTrue(everyQuality.entries.none { it.trackId == "a" })
    }

    @Test
    fun mergePrefersIncomingEntriesAndAppendsNewOnes() {
        val base = DownloadManifest(entries = listOf(entry("a"), entry("b")))
        val incoming = DownloadManifest(
            entries = listOf(entry("a", fileName = "a-updated.mp3"), entry("c")),
        )

        val merged = DownloadManifestOps.merge(base, incoming)

        assertEquals(listOf("a", "b", "c"), merged.entries.map { it.trackId })
        assertEquals("a-updated.mp3", merged.entries.first().fileName)
    }
}
