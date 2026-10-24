package com.banlimusic.app.core.common

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {

    /** NFKC + lower-case + keep letters/digits only. */
    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        return buildString {
            decomposed.codePoints().forEach { codePoint ->
                if (Character.isLetterOrDigit(codePoint)) {
                    appendCodePoint(codePoint)
                }
            }
        }
    }

    fun normalizeArtist(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        val featIndex = listOf(lower.indexOf(" feat."), lower.indexOf(" ft."))
            .filter { it >= 0 }
            .minOrNull()
        val withoutFeat = if (featIndex != null) name.substring(0, featIndex) else name
        return normalize(withoutFeat)
    }

    fun artistKeys(names: List<String>): Set<String> = names
        .flatMap { raw ->
            raw.split('/', ',', '&', '、', '，', ';', '；')
        }
        .map(::normalizeArtist)
        .filter { it.isNotBlank() }
        .toSet()

    fun formatDurationMs(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0) return "--:--"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }

    fun sanitizeFileName(value: String): String {
        val cleaned = value
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.ifBlank { "track" }.take(120)
    }
}
