package com.banlimusic.app.domain.sort

import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSorterTest {

    private fun track(id: String, title: String, artist: String) = Track(
        id = id,
        title = title,
        artists = listOf(Artist(name = artist)),
    )

    @Test
    fun sectionsFollowSortedOrder() {
        val tracks = listOf(
            track("1", "晴天", "周杰伦"),
            track("2", "Love Story", "Taylor Swift"),
            track("3", "123", "Unknown"),
            track("4", "七里香", "周杰伦"),
        )
        val sorted = TrackSorter.sort(tracks, TrackSort.TITLE, ascending = true)
        val sections = TrackSorter.sections(sorted, TrackSort.TITLE)
        assertEquals(listOf("#", "L", "Q"), sections.map { it.label })
        assertEquals(0, sections.first().itemIndex)
        assertTrue(sections.zipWithNext().all { (a, b) -> a.itemIndex < b.itemIndex })
    }

    @Test
    fun timeSortsKeepInputOrderAndHaveNoSections() {
        val tracks = listOf(
            track("3", "Newest", "A"),
            track("2", "Middle", "B"),
            track("1", "Oldest", "C"),
        )

        assertEquals(tracks, TrackSorter.sort(tracks, TrackSort.RECENT, ascending = true))
        assertEquals(tracks.reversed(), TrackSorter.sort(tracks, TrackSort.RECENT, ascending = false))
        assertTrue(TrackSorter.sections(tracks, TrackSort.RECENT).isEmpty())
        assertTrue(TrackSorter.sections(tracks, TrackSort.CREATED).isEmpty())
    }

    @Test
    fun descendingSectionsAreReversed() {
        val tracks = listOf(
            track("1", "晴天", "周杰伦"),
            track("2", "Love Story", "Taylor Swift"),
        )
        val sorted = TrackSorter.sort(tracks, TrackSort.TITLE, ascending = false)
        val sections = TrackSorter.sections(sorted, TrackSort.TITLE)
        assertEquals(listOf("Q", "L"), sections.map { it.label })
    }
}
