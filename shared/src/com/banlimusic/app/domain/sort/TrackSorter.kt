package com.banlimusic.app.domain.sort

import com.banlimusic.app.core.common.InitialIndexer
import com.banlimusic.app.core.model.Track

enum class TrackSort(val label: String) {
    TITLE("名称"),
    ARTIST("歌手"),
    ALBUM("专辑"),
}

data class AlphabetSection(val label: String, val itemIndex: Int)

object TrackSorter {

    fun sort(tracks: List<Track>, sort: TrackSort, ascending: Boolean): List<Track> {
        val comparator = compareBy<Track> { sectionKey(it, sort) }
            .thenBy { field(it, sort).lowercase() }
            .thenBy { it.title.lowercase() }
        return tracks.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    fun sections(sortedTracks: List<Track>, sort: TrackSort): List<AlphabetSection> {
        val sections = mutableListOf<AlphabetSection>()
        sortedTracks.forEachIndexed { index, track ->
            val label = sectionKey(track, sort)
            if (sections.lastOrNull()?.label != label) {
                sections += AlphabetSection(label = label, itemIndex = index)
            }
        }
        return sections
    }

    fun sectionKey(track: Track, sort: TrackSort): String =
        InitialIndexer.key(field(track, sort))

    private fun field(track: Track, sort: TrackSort): String = when (sort) {
        TrackSort.TITLE -> track.title
        TrackSort.ARTIST -> track.artistLine
        TrackSort.ALBUM -> track.album?.title.orEmpty()
    }
}
