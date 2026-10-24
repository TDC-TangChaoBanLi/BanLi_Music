package com.banlimusic.app.core.datastore

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomSourceSettingsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun reservedSourceSettingsRoundTrip() {
        val source = CustomSourceSettings(
            id = "my-server",
            displayName = "自建音乐库",
            providerCode = "my",
            trackIdFormat = "{source}:{id}",
            qualities = listOf(QualityOption(code = "hq", kbps = 320, label = "高品")),
            enabled = false,
            searchEndpoint = ApiEndpointSettings(
                urlTemplate = "https://example.test/search",
                queryParams = mapOf("q" to "{query}"),
                resultListPath = "data.songs",
            ),
        )
        val decoded = json.decodeFromString<CustomSourceSettings>(
            json.encodeToString(CustomSourceSettings.serializer(), source),
        )
        assertEquals(source, decoded)
    }

    @Test
    fun missingFieldsUseDefaults() {
        val decoded = json.decodeFromString<CustomSourceSettings>("{}")
        assertEquals("", decoded.displayName)
        assertEquals(emptyList<QualityOption>(), decoded.qualities)
        assertEquals(ApiEndpointSettings(), decoded.searchEndpoint)
    }
}
