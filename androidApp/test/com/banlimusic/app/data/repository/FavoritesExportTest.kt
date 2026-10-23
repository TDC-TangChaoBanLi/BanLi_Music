package com.banlimusic.app.data.repository

import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesExportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun roundTripKeepsCategoryAndTrackMetadata() {
        val track = Track(
            id = "gd-netease:42",
            title = "Example",
            artists = listOf(Artist(name = "Singer")),
            sources = listOf(
                TrackSource(
                    providerId = "gd-netease",
                    providerTrackId = "42",
                    qualityKbps = 320,
                ),
            ),
        )
        val export = FavoritesExport(
            categories = listOf("默认收藏", "通勤"),
            favorites = listOf(
                FavoriteExportItem(category = "通勤", addedAt = 123L, track = track),
            ),
        )

        val raw = json.encodeToString(FavoritesExport.serializer(), export)
        val decoded = json.decodeFromString(FavoritesExport.serializer(), raw)

        assertEquals(export, decoded)
        assertEquals("通勤", decoded.favorites.single().category)
        assertEquals("Example", decoded.favorites.single().track.title)
        assertEquals("gd-netease", decoded.favorites.single().track.sources.single().providerId)
    }
}
