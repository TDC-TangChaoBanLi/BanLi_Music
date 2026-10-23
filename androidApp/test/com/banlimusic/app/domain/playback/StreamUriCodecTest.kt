package com.banlimusic.app.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUriCodecTest {

    @Test
    fun roundTripsCandidatesWithSpecialCharacters() {
        val candidates = listOf(
            StreamCandidate("gd-netease", "123456/abc"),
            StreamCandidate("gd-joox", "中文 id: 42"),
        )

        val decoded = StreamUriCodec.decode(StreamUriCodec.encode(candidates))

        assertEquals(candidates, decoded)
    }

    @Test
    fun emptyInputProducesNoCandidates() {
        assertEquals(emptyList<StreamCandidate>(), StreamUriCodec.decode(StreamUriCodec.encode(emptyList())))
        assertEquals(emptyList<StreamCandidate>(), StreamUriCodec.decode("not-base64-***"))
    }

    @Test
    fun encodedPayloadIsUrlSafe() {
        val encoded = StreamUriCodec.encode(listOf(StreamCandidate("p", "a/b?c=d")))

        assertEquals(false, encoded.contains('/'))
        assertEquals(false, encoded.contains('?'))
        assertEquals(false, encoded.contains('='))
    }
}
