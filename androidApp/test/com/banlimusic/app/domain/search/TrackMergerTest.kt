package com.banlimusic.app.domain.search

import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMergerTest {

    private val merger = TrackMerger()

    @Test
    fun mergesSameSongFromDifferentProviders() {
        val netease = track("netease:id1", "晴天", listOf("周杰伦"), provider = "gd-netease")
        val joox = track("joox:id2", "晴天", listOf("周杰伦"), provider = "gd-joox")

        val merged = merger.merge(listOf(netease, joox))

        assertEquals(1, merged.size)
        assertEquals(2, merged.first().sources.size)
    }

    @Test
    fun keepsLiveVersionSeparate() {
        val studio = track("a", "晴天", listOf("周杰伦"), provider = "p1")
        val live = track("b", "晴天 (Live)", listOf("周杰伦"), provider = "p2")

        val merged = merger.merge(listOf(studio, live))

        assertEquals(2, merged.size)
    }

    @Test
    fun keepsFullWidthBracketsAsVersionTags() {
        val studio = track("a", "七里香", listOf("周杰伦"), provider = "p1")
        val live = track("b", "七里香（现场版）", listOf("周杰伦"), provider = "p2")

        val merged = merger.merge(listOf(studio, live))

        assertEquals(2, merged.size)
    }

    @Test
    fun doesNotMergeDifferentArtists() {
        val original = track("a", "月光", listOf("某人"), provider = "p1")
        val cover = track("b", "月光", listOf("另一个人"), provider = "p2")

        val merged = merger.merge(listOf(original, cover))

        assertEquals(2, merged.size)
    }

    @Test
    fun normalizesArtistOrderAndFeaturing() {
        val a = track("a", "珊瑚海", listOf("周杰伦", "梁心颐"), provider = "p1")
        val b = track("b", "珊瑚海", listOf("梁心颐 feat. 周杰伦"), provider = "p2")

        assertTrue(merger.isSameTrack(a, b))
    }

    @Test
    fun rejectsLargeDurationMismatch() {
        val a = track("a", "夜曲", listOf("周杰伦"), durationMs = 200_000, provider = "p1")
        val b = track("b", "夜曲", listOf("周杰伦"), durationMs = 260_000, provider = "p2")

        assertFalse(merger.isSameTrack(a, b))
    }

    @Test
    fun acceptsSmallDurationMismatch() {
        val a = track("a", "夜曲", listOf("周杰伦"), durationMs = 200_000, provider = "p1")
        val b = track("b", "夜曲", listOf("周杰伦"), durationMs = 203_500, provider = "p2")

        assertTrue(merger.isSameTrack(a, b))
    }

    @Test
    fun mergeKeepsAllSourcesAndNonEmptyMetadata() {
        val a = track("a", "夜曲", listOf("周杰伦"), durationMs = null, provider = "p1")
        val b = track("b", "夜曲", listOf("周杰伦"), durationMs = 200_000, provider = "p2")

        val merged = merger.merge(listOf(a, b)).single()

        assertEquals(200_000L, merged.durationMs)
        assertEquals(setOf("p1", "p2"), merged.sources.map { it.providerId }.toSet())
    }

    @Test
    fun stableIdDoesNotDependOnProviderOrder() {
        val netease = track("netease:id1", "晴天", listOf("周杰伦"), provider = "gd-netease")
        val joox = track("joox:id2", "晴天", listOf("周杰伦"), provider = "gd-joox")

        val firstOrder = merger.merge(listOf(netease, joox)).single()
        val secondOrder = merger.merge(listOf(joox, netease)).single()

        assertEquals(firstOrder.id, secondOrder.id)
        assertTrue(firstOrder.id.startsWith("trk-"))
    }

    @Test
    fun stableIdDiffersBetweenVersions() {
        val studio = merger.merge(listOf(track("a", "晴天", listOf("周杰伦"), provider = "p1"))).single()
        val live = merger.merge(
            listOf(track("b", "晴天 (Live)", listOf("周杰伦"), provider = "p2")),
        ).single()

        assertFalse(studio.id == live.id)
    }

    @Test
    fun relevanceRanksExactTitleFirst() {
        val exact = track("a", "晴天", listOf("周杰伦"), provider = "p1")
        val partial = track("b", "晴天vlog背景音乐", listOf("别人"), provider = "p2")

        assertTrue(merger.relevance(exact, "晴天") > merger.relevance(partial, "晴天"))
    }

    private fun track(
        id: String,
        title: String,
        artists: List<String>,
        durationMs: Long? = null,
        provider: String,
    ): Track = Track(
        id = id,
        title = title,
        artists = artists.map { Artist(name = it) },
        durationMs = durationMs,
        sources = listOf(
            TrackSource(
                providerId = provider,
                providerTrackId = id,
                availableActions = setOf(TrackAction.STREAM),
            ),
        ),
    )
}
