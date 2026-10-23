package com.banlimusic.app.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun roundTripsTrackStoredInRoom() {
        val track = Track(
            id = "gd-netease:42",
            title = "晴天",
            artists = listOf(Artist(id = "1", name = "周杰伦")),
            album = Album(id = "2", title = "叶惠美"),
            durationMs = 269_000L,
            artworkUrl = "https://example.com/a.jpg",
            sources = listOf(
                TrackSource(
                    providerId = "gd-netease",
                    providerTrackId = "42",
                    availableActions = setOf(TrackAction.STREAM, TrackAction.DOWNLOAD),
                    artworkKey = "pic",
                    lyricKey = "lyric",
                    qualityKbps = 320,
                ),
                TrackSource(providerId = "gd-joox", providerTrackId = "abc"),
            ),
            isExplicit = false,
        )

        val raw = json.encodeToString(Track.serializer(), track)
        val decoded = json.decodeFromString(Track.serializer(), raw)

        assertEquals(track, decoded)
    }

    @Test
    fun decodesOlderJsonWithoutNewFields() {
        val raw = """{"id":"x","title":"Song","sources":[{"providerId":"p","providerTrackId":"1"}]}"""

        val track = json.decodeFromString(Track.serializer(), raw)

        assertEquals("Song", track.title)
        assertEquals(setOf(TrackAction.STREAM), track.sources.single().availableActions)
    }
}
