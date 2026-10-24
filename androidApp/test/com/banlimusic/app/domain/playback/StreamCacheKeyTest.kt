package com.banlimusic.app.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCacheKeyTest {

    @Test
    fun cacheKeyIsStableAndParsable() {
        val key = StreamUriCodec.cacheKey("trk-abc", 320)
        assertEquals("banli:v1:trk-abc:320", key)
        assertEquals("trk-abc", StreamUriCodec.trackIdOfCacheKey(key))
        assertTrue(key.startsWith(StreamUriCodec.trackCachePrefix("trk-abc")))
    }

    @Test
    fun uriRoundTripsMediaId() {
        val candidates = listOf(
            StreamCandidate("gd-netease", "123456/abc"),
            StreamCandidate("gd-joox", "中文 id: 42"),
        )
        val uri = StreamUriCodec.toUriString(candidates, "trk-中文:1")
        assertEquals(candidates, StreamUriCodec.decode(StreamUriCodec.encodedOf(uri).orEmpty()))
        assertEquals("trk-中文:1", StreamUriCodec.mediaIdOf(uri))
    }
}
