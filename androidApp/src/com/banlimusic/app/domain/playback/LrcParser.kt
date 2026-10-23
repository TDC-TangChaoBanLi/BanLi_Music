package com.banlimusic.app.domain.playback

import com.banlimusic.app.core.common.LrcLine
import java.util.Locale

object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(raw: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        raw.lineSequence().forEach { line ->
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.replace(timeTag, "").trim()
            if (text.isEmpty()) return@forEach
            tags.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                lines += LrcLine(timeMs = minutes * 60_000 + seconds * 1_000 + millis, text = text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}

fun String.stripTimestamps(): String = lineSequence()
    .map { it.replace(Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]"), "").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")

fun LrcLine.isNear(positionMs: Long, windowMs: Long = 500L): Boolean =
    timeMs <= positionMs && positionMs - timeMs < windowMs

fun formatLyricTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
