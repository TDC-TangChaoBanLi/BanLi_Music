package com.banlimusic.app.data.source.gd

import com.banlimusic.app.core.common.MusicException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GdParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesRealSearchArray() {
        val payload = """
            [{"id":"2652820720","name":"晴天(深情版)","artist":["Lucky小爱"],
            "album":"晴天(深情版)","pic_id":"109951173569626660",
            "url_id":"2652820720","lyric_id":"2652820720","source":"netease",
            "from":"music.gdstudio.xyz"}]
        """.trimIndent()

        val items = json.parseToJsonElement(payload).searchItems()

        assertEquals(1, items.size)
        assertEquals("2652820720", items[0]["id"].stringOrNull())
        assertEquals(listOf("Lucky小爱"), items[0]["artist"].stringList())
        assertEquals("109951173569626660", items[0]["pic_id"].stringOrNull())
        assertEquals("2652820720", items[0]["lyric_id"].stringOrNull())
    }

    @Test
    fun parsesNumericIdsAndSingleArtistString() {
        val payload = """
            [{"id":42,"name":"Song","artist":"Artist A / Artist B","album":"Album"}]
        """.trimIndent()

        val item = json.parseToJsonElement(payload).searchItems().single()

        assertEquals("42", item["id"].stringOrNull())
        assertEquals(listOf("Artist A / Artist B"), item["artist"].stringList())
    }

    @Test
    fun unwrapsDataArray() {
        val payload = """{"data":[{"id":"1","name":"A"}]}"""

        assertEquals(1, json.parseToJsonElement(payload).searchItems().size)
    }

    @Test
    fun surfacesApiErrorMessage() {
        val payload = """{"error":"暂不支持该音乐源"}"""

        val error = assertThrows(MusicException.Unavailable::class.java) {
            json.parseToJsonElement(payload).searchItems()
        }
        assertEquals("暂不支持该音乐源", error.message)
    }

    @Test
    fun helperHandlesMissingValues() {
        val payload = """{"url":null,"br":320}"""

        val obj = json.parseToJsonElement(payload) as JsonObject
        assertNull(obj["url"].stringOrNull())
        assertEquals(320, obj["br"].intOrNull())
        assertNull(obj["missing"].stringOrNull())
    }
}
