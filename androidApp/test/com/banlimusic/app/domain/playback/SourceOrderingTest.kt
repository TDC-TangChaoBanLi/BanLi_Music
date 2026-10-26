package com.banlimusic.app.domain.playback

import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceOrderingTest {

    private fun source(providerId: String): TrackSource = TrackSource(
        providerId = providerId,
        providerTrackId = "$providerId-id",
    )

    private fun track(vararg providerIds: String): Track = Track(
        id = "track",
        title = "Song",
        artists = listOf(Artist(name = "Artist")),
        sources = providerIds.map(::source),
    )

    @Test
    fun preferredProviderMovesToFrontAndKeepsOrder() {
        val sources = listOf(source("gd-netease"), source("gd-joox"), source("gd-bilibili"))

        val ordered = SourceOrdering.reorderSources(sources, "gd-joox")

        assertEquals(
            listOf("gd-joox", "gd-netease", "gd-bilibili"),
            ordered.map { it.providerId },
        )
    }

    @Test
    fun unknownOrMissingPreferenceKeepsOriginalOrder() {
        val sources = listOf(source("gd-netease"), source("gd-joox"))

        assertEquals(sources, SourceOrdering.reorderSources(sources, null))
        assertEquals(sources, SourceOrdering.reorderSources(sources, "gd-bilibili"))
        assertEquals(sources, SourceOrdering.reorderSources(sources, "gd-netease"))
    }

    @Test
    fun streamCandidatesSkipNonStreamableSources() {
        val track = Track(
            id = "track",
            title = "Song",
            sources = listOf(
                source("gd-netease").copy(availableActions = emptySet()),
                source("gd-joox"),
            ),
        )

        val candidates = SourceOrdering.streamCandidates(track, preferredProviderId = "gd-joox")

        assertEquals(listOf(StreamCandidate("gd-joox", "gd-joox-id")), candidates)
    }

    @Test
    fun rotateStartsRetryFromTheNextSource() {
        val candidates = listOf(
            StreamCandidate("a", "1"),
            StreamCandidate("b", "2"),
            StreamCandidate("c", "3"),
        )

        assertEquals(candidates, SourceOrdering.rotate(candidates, 0))
        assertEquals(listOf("b", "c", "a"), SourceOrdering.rotate(candidates, 1).map { it.providerId })
        assertEquals(listOf("c", "a", "b"), SourceOrdering.rotate(candidates, 2).map { it.providerId })
        assertEquals(listOf("b", "c", "a"), SourceOrdering.rotate(candidates, 4).map { it.providerId })
    }

    @Test
    fun rotateKeepsSingleCandidateUntouched() {
        val candidates = listOf(StreamCandidate("a", "1"))

        assertEquals(candidates, SourceOrdering.rotate(candidates, 3))
        assertTrue(SourceOrdering.rotate(emptyList<StreamCandidate>(), 1).isEmpty())
    }
}
