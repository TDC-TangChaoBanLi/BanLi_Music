package com.banlimusic.app.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun parsesTimestampsAndSorts() {
        val raw = """
            [00:12.50]第二句
            [00:01.00]第一句
            [00:20]最后一句
        """.trimIndent()

        val lines = LrcParser.parse(raw)

        assertEquals(3, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals("第一句", lines[0].text)
        assertEquals(12_500L, lines[1].timeMs)
        assertEquals(20_000L, lines[2].timeMs)
    }

    @Test
    fun parsesMultipleTagsOnOneLine() {
        val lines = LrcParser.parse("[00:01.00][00:03.00]重复")

        assertEquals(2, lines.size)
        assertEquals(listOf(1_000L, 3_000L), lines.map { it.timeMs })
    }

    @Test
    fun stripsTimestampsForPlainTextDisplay() {
        val raw = "[00:01.00]第一句\n[00:02.50]第二句"

        assertEquals("第一句\n第二句", raw.stripTimestamps())
    }

    @Test
    fun ignoresLinesWithoutTimestamps() {
        val lines = LrcParser.parse("作词：某人\n[00:01.00]第一句")

        assertEquals(1, lines.size)
    }
}
