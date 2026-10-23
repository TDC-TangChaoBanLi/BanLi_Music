package com.banlimusic.app.domain.search

import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.model.Track
import java.security.MessageDigest
import kotlin.math.abs

data class ParsedTitle(val base: String, val qualifiers: Set<String>)

/**
 * Normalizes titles while keeping version tags (live/remix/instrumental/...)
 * as a separate signal so that different versions are not merged incorrectly.
 */
object TrackTitleParser {

    private val bracketRegex = Regex("[\\[(（【]([^)）】\\]]*)[)）】\\]]")
    private val dashSuffixRegex = Regex("\\s+-\\s+(.+)$")

    private val latinVersionWords = listOf(
        "live", "remix", "dj", "instrumental", "karaoke", "acoustic", "cover",
        "demo", "sped up", "slowed", "8d", "off vocal", "inst", "remaster",
        "explicit", "clean", "nightcore", "orchestral", "piano",
    )

    private val chineseVersionWords = listOf(
        "现场", "现场版", "混音", "伴奏", "翻唱", "重制", "重制版", "加速", "降速",
        "纯音乐", "钢琴版", "吉他版", "女声版", "男声版", "片段", "完整版",
    )

    fun parse(title: String): ParsedTitle {
        val qualifierTexts = mutableListOf<String>()
        var stripped = title

        bracketRegex.findAll(title).forEach { match ->
            qualifierTexts += match.groupValues[1]
            stripped = stripped.replace(match.value, " ")
        }
        dashSuffixRegex.find(stripped)?.let { match ->
            qualifierTexts += match.groupValues[1]
            stripped = stripped.substring(0, match.range.first)
        }
        // Trailing version words without separators, e.g. "Song Live".
        val lowerStripped = stripped.lowercase()
        latinVersionWords.forEach { word ->
            val regex = Regex("(?i)\\b${Regex.escape(word)}\\b")
            if (regex.containsMatchIn(lowerStripped)) {
                qualifierTexts += word
                stripped = regex.replace(stripped, " ")
            }
        }
        chineseVersionWords.forEach { word ->
            if (stripped.contains(word)) {
                qualifierTexts += word
                stripped = stripped.replace(word, " ")
            }
        }

        return ParsedTitle(
            base = TextNormalizer.normalize(stripped),
            qualifiers = qualifierTexts.flatMap(::extractTags).toSet(),
        )
    }

    private fun extractTags(text: String): Set<String> {
        val tags = mutableSetOf<String>()
        val raw = text.lowercase()
        latinVersionWords.forEach { word ->
            val regex = Regex("\\b${Regex.escape(word)}\\b")
            if (regex.containsMatchIn(raw)) tags += word
        }
        chineseVersionWords.forEach { word ->
            if (raw.contains(word)) tags += word
        }
        // A remaster is normally the same recording; do not split on it alone.
        tags.remove("remaster")
        return tags
    }
}

/** Uses normalized title, artists, version tags and duration to merge duplicates. */
class TrackMerger(private val durationToleranceMs: Long = 5_000L) {

    fun merge(tracks: List<Track>): List<Track> {
        val groups = mutableListOf<MutableList<Track>>()
        tracks.forEach { track ->
            val group = groups.firstOrNull { isSameTrack(it.first(), track) }
            if (group == null) {
                groups += mutableListOf(track)
            } else {
                group += track
            }
        }
        return groups.map(::mergeGroup)
    }

    fun isSameTrack(a: Track, b: Track): Boolean {
        val titleA = TrackTitleParser.parse(a.title)
        val titleB = TrackTitleParser.parse(b.title)
        if (titleA.base.isBlank() || titleA.base != titleB.base) return false
        if (titleA.qualifiers != titleB.qualifiers) return false

        val artistsA = TextNormalizer.artistKeys(a.artists.map { it.name })
        val artistsB = TextNormalizer.artistKeys(b.artists.map { it.name })
        if (artistsA.isNotEmpty() && artistsB.isNotEmpty() && artistsA.intersect(artistsB).isEmpty()) {
            return false
        }

        val durationA = a.durationMs
        val durationB = b.durationMs
        if (durationA != null && durationB != null && abs(durationA - durationB) > durationToleranceMs) {
            return false
        }
        return true
    }

    private fun mergeGroup(group: List<Track>): Track {
        val primary = group.first()
        val sources = group
            .flatMap { it.sources }
            .distinctBy { it.providerId to it.providerTrackId }
        val album = group.firstNotNullOfOrNull { it.album }
        return primary.copy(
            id = stableId(group),
            artists = group.firstOrNull { it.artists.isNotEmpty() }?.artists ?: primary.artists,
            album = album,
            durationMs = group.firstNotNullOfOrNull { it.durationMs },
            artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl },
            sources = sources,
            isExplicit = group.any { it.isExplicit },
        )
    }

    /**
     * Application-owned identifier derived from normalized metadata, so the same
     * song keeps its id regardless of which provider returned it first.
     */
    fun stableId(group: List<Track>): String {
        val parsed = TrackTitleParser.parse(group.first().title)
        if (parsed.base.isBlank()) return group.first().id
        val artistKey = group
            .flatMap { TextNormalizer.artistKeys(it.artists.map { artist -> artist.name }) }
            .minOrNull()
            .orEmpty()
        val qualifiers = parsed.qualifiers.sorted().joinToString(",")
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("${parsed.base}|$artistKey|$qualifiers".toByteArray())
        val hash = digest.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "trk-" + hash.take(20)
    }

    fun relevance(track: Track, query: String): Int {
        val normalizedQuery = TextNormalizer.normalize(query)
        if (normalizedQuery.isBlank()) return 0
        val title = TextNormalizer.normalize(track.title)
        val artists = TextNormalizer.normalize(track.artistLine)
        return when {
            title == normalizedQuery -> 100
            title.startsWith(normalizedQuery) -> 80
            title.contains(normalizedQuery) -> 60
            artists.contains(normalizedQuery) -> 40
            else -> 10
        }
    }
}
