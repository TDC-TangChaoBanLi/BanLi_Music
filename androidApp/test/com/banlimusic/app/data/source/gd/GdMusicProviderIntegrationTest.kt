package com.banlimusic.app.data.source.gd

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.limiter.SlidingWindowRateLimiter
import com.banlimusic.app.core.network.JsonHttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GdMusicProviderIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GdMusicProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val scheduler = RequestScheduler(
            maxConcurrent = 2,
            limiter = SlidingWindowRateLimiter(limit = 50, windowMs = 300_000L),
        )
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val api = GdApiClient(
            http = JsonHttpClient(httpClient, json, scheduler),
            scheduler = scheduler,
            baseUrl = server.url("/api.php"),
        )
        provider = GdMusicProvider(api, "netease", "网易云音乐")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchesAndMapsResponseFields() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"42","name":"晴天","artist":["周杰伦"],"album":"叶惠美",
                   "pic_id":"pic-1","lyric_id":"lyric-1","source":"netease"}]""",
            ),
        )

        val page = provider.search("晴天", page = 1, pageSize = 20)

        assertEquals(1, page.tracks.size)
        val track = page.tracks.single()
        assertEquals("晴天", track.title)
        assertEquals("周杰伦", track.artistLine)
        assertEquals("叶惠美", track.album?.title)
        assertEquals("gd-netease", track.sources.single().providerId)
        assertEquals("42", track.sources.single().providerTrackId)
        assertEquals("pic-1", track.sources.single().artworkKey)
        assertEquals("lyric-1", track.sources.single().lyricKey)
        assertEquals(false, page.hasMore)

        val request = server.takeRequest()
        assertEquals("search", request.requestUrl?.queryParameter("types"))
        assertEquals("netease", request.requestUrl?.queryParameter("source"))
        assertEquals("晴天", request.requestUrl?.queryParameter("name"))
    }

    @Test
    fun resolvesStreamAndKeepsByteSize() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"url":"https://example.com/song.mp3","br":320,"size":11161644}""",
            ),
        )

        val stream = provider.resolveStream("42", qualityKbps = 320)

        assertEquals("https://example.com/song.mp3", stream.url)
        assertEquals(320, stream.bitrateKbps)
        assertEquals(11_161_644L, stream.sizeBytes)
    }

    @Test
    fun resolvesArtworkAndLyrics() = runTest {
        server.enqueue(MockResponse().setBody("""{"url":"https://example.com/cover.jpg"}"""))
        server.enqueue(MockResponse().setBody("""{"lyric":"[00:01.00]line","tlyric":"[00:01.00]翻译"}"""))

        val artwork = provider.getArtworkUrl("pic-1")
        val lyrics = provider.getLyrics("42", "lyric-1")

        assertEquals("https://example.com/cover.jpg", artwork)
        assertNotNull(lyrics)
        assertEquals("[00:01.00]line", lyrics?.raw)
        assertEquals("[00:01.00]翻译", lyrics?.translated)
    }

    @Test
    fun mapsHttpErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(MusicException.Server::class.java) {
            kotlinx.coroutines.runBlocking { provider.search("x", 1, 20) }
        }
    }

    @Test
    fun surfacesApiErrorObject() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":"暂不支持该音乐源"}"""))

        val error = assertThrows(MusicException.Unavailable::class.java) {
            kotlinx.coroutines.runBlocking { provider.search("x", 1, 20) }
        }
        assertTrue(error.message.orEmpty().contains("暂不支持"))
    }
}
