package com.banlimusic.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialIndexerTest {

    @Test
    fun latinAndDigits() {
        assertEquals("L", InitialIndexer.key("Love Story"))
        assertEquals("A", InitialIndexer.key("abc"))
        assertEquals("#", InitialIndexer.key("123"))
        assertEquals("#", InitialIndexer.key(""))
    }

    @Test
    fun commonChineseInitials() {
        assertEquals("Q", InitialIndexer.key("晴天"))
        assertEquals("Z", InitialIndexer.key("周杰伦"))
        assertEquals("W", InitialIndexer.key("我的天空"))
    }

    @Test
    fun rareCharactersFallBackWithoutCrashing() {
        assertEquals("#", InitialIndexer.key("𠮷野家"))
    }
}
