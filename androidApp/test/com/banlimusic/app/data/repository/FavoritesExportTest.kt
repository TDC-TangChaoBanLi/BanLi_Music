package com.banlimusic.app.data.repository

import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun roundTripKeepsEveryFolder() {
        val track = Track(id = "gd-netease:7", title = "Multi")
        val export = FavoritesExport(
            version = 2,
            categories = listOf("默认收藏", "通勤", "跑步"),
            favorites = listOf(
                FavoriteExportItem(
                    category = "默认收藏",
                    addedAt = 456L,
                    track = track,
                    folders = listOf("默认收藏", "通勤"),
                ),
            ),
        )

        val raw = json.encodeToString(FavoritesExport.serializer(), export)
        val decoded = json.decodeFromString(FavoritesExport.serializer(), raw)

        assertEquals(2, decoded.version)
        assertEquals(listOf("默认收藏", "通勤"), decoded.favorites.single().folders)
        assertEquals("默认收藏", decoded.favorites.single().category)
    }

    @Test
    fun decodesVersionOneBackupWithoutFolders() {
        val track = Track(id = "local:1", title = "Legacy")
        val trackJson = json.encodeToString(Track.serializer(), track)
        val raw = """
            {
              "version": 1,
              "categories": ["通勤"],
              "favorites": [
                {"category": "通勤", "addedAt": 123, "track": $trackJson}
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(FavoritesExport.serializer(), raw)

        assertEquals(1, decoded.version)
        assertEquals("通勤", decoded.favorites.single().category)
        assertTrue(decoded.favorites.single().folders.isEmpty())
    }
}
