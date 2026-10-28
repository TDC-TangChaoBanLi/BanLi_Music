package com.banlimusic.app.core.common

import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight A-Z key for list grouping. Latin letters map to themselves,
 * digits/symbols map to "#", and common simplified Chinese characters map to
 * their pinyin initial through the GB2312 code ranges. Rare/traditional
 * characters and polyphonic characters may fall back to "#" or an approximate
 * initial; no external pinyin dictionary is bundled.
 */
object InitialIndexer {
    private val cache = ConcurrentHashMap<Char, String>()
    // jdk.charsets provides these on the JVM; Android has them in the platform.
    // GBK/GB18030 are supersets of GB2312 and keep the same GB2312 byte ranges.
    private val gbCharsets = listOf("GB2312", "GBK", "GB18030").mapNotNull { name ->
        runCatching { Charset.forName(name) }.getOrNull()
    }

    // Start code of each pinyin initial in GB2312 order. I, U and V do not exist
    // as pinyin initials.
    private val boundaries = intArrayOf(
        0xB0A1, // A
        0xB0C5, // B
        0xB2C1, // C
        0xB4EE, // D
        0xB6EA, // E
        0xB7A2, // F
        0xB8C1, // G
        0xB9FE, // H
        0xBBF7, // J
        0xBFA6, // K
        0xC0AC, // L
        0xC2E8, // M
        0xC4C3, // N
        0xC5B6, // O
        0xC5BE, // P
        0xC6DA, // Q
        0xC8BB, // R
        0xC8F6, // S
        0xCBFA, // T
        0xCDDA, // W
        0xCEF4, // X
        0xD1B9, // Y
        0xD4D1, // Z
    )
    private val letters = "ABCDEFGHJKLMNOPQRSTWXYZ"

    fun key(value: String): String {
        val first = value.trim().firstOrNull() ?: return "#"
        return cache.getOrPut(first) {
            when {
                first in 'A'..'Z' -> first.toString()
                first in 'a'..'z' -> first.uppercaseChar().toString()
                first in '0'..'9' -> "#"
                first.code >= 0x4E00 && first.code <= 0x9FFF -> chineseInitial(first)
                else -> "#"
            }
        }
    }

    private fun chineseInitial(char: Char): String {
        val bytes = gbCharsets.firstNotNullOfOrNull { charset ->
            runCatching { char.toString().toByteArray(charset) }
                .getOrNull()
                ?.takeIf { it.size == 2 }
        } ?: return "#"
        if (bytes.size != 2) return "#"
        val code = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        if (code < boundaries.first() || code > 0xF7FE) return "#"
        var result = "#"
        for (index in boundaries.indices) {
            if (code >= boundaries[index]) result = letters[index].toString() else break
        }
        return result
    }
}
