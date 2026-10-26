package com.banlimusic.app.domain.sort

import com.banlimusic.app.core.common.InitialIndexer
import com.banlimusic.app.core.model.Track

enum class TrackSort(val label: String) {
    TITLE("名称"),
    ARTIST("歌手"),
    ALBUM("专辑"),

    /** Play history order: the source list is already sorted by last played time. */
    RECENT("播放时间"),

    /** Download order: the source list is already sorted by download time. */
    CREATED("下载时间"),
    ;

    val isTimeBased: Boolean get() = this == RECENT || this == CREATED
}

data class AlphabetSection(val label: String, val itemIndex: Int)

object TrackSorter {

    fun sort(tracks: List<Track>, sort: TrackSort, ascending: Boolean): List<Track> {
        if (sort.isTimeBased) {
            // Time based lists arrive already ordered (newest first); the toggle
            // only flips that natural order because tracks carry no timestamp.
            return if (ascending) tracks else tracks.reversed()
        }
        val comparator = compareBy<Track> { sectionKey(it, sort) }
            .thenBy { field(it, sort).lowercase() }
            .thenBy { it.title.lowercase() }
        return tracks.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    fun sections(sortedTracks: List<Track>, sort: TrackSort): List<AlphabetSection> {
        if (sort.isTimeBased) return emptyList()
        val sections = mutableListOf<AlphabetSection>()
        sortedTracks.forEachIndexed { index, track ->
            val label = sectionKey(track, sort)
            if (sections.lastOrNull()?.label != label) {
                sections += AlphabetSection(label = label, itemIndex = index)
            }
        }
        return sections
    }

    fun sectionKey(track: Track, sort: TrackSort): String = when (sort) {
        TrackSort.RECENT, TrackSort.CREATED -> "#"
        else -> InitialIndexer.key(field(track, sort))
    }

    private fun field(track: Track, sort: TrackSort): String = when (sort) {
        TrackSort.TITLE, TrackSort.RECENT, TrackSort.CREATED -> track.title
        TrackSort.ARTIST -> track.artistLine
        TrackSort.ALBUM -> track.album?.title.orEmpty()
    }
}
