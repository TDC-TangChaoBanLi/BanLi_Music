package com.banlimusic.app.data.sync

import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.limiter.SlidingWindowRateLimiter
import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.core.model.SyncFolder
import com.banlimusic.app.core.model.SyncMembership
import com.banlimusic.app.core.model.SyncTrack
import com.banlimusic.app.core.model.Track
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OneDriveClientTest {

    private lateinit var server: MockWebServer

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val document = FavoriteSyncDocument(
        folders = listOf(SyncFolder("默认收藏", 1L, 1L)),
        tracks = listOf(
            SyncTrack(
                trackId = "local:1",
                track = Track(id = "local:1", title = "Example"),
                addedAt = 1L,
                updatedAt = 1L,
            ),
        ),
        memberships = listOf(SyncMembership("local:1", "默认收藏", 1L, 1L)),
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        tokenProvider: AccessTokenProvider = AccessTokenProvider { "token" },
    ): OneDriveClient {
        val scheduler = RequestScheduler(
            maxConcurrent = 2,
            limiter = SlidingWindowRateLimiter(
                limit = 1_000,
                windowMs = 60_000L,
                maxWaitMs = 1_000L,
            ),
        )
        return OneDriveClient(
            client = OkHttpClient.Builder().build(),
            json = json,
            tokenProvider = tokenProvider,
            graphBaseUrl = server.url("/v1.0").toString().trimEnd('/'),
            scheduler = scheduler,
        )
    }

    @Test
    fun downloadReturnsNullWhenFileMissing() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client().downloadLibrary()

        assertNull(result.document)
        assertNull(result.etag)
    }

    @Test
    fun downloadParsesMetadataAndContent() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"file-1","eTag":"etag-1","size":42}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(FavoriteSyncDocument.serializer(), document)),
        )

        val result = client().downloadLibrary()

        assertEquals("etag-1", result.etag)
        assertEquals("默认收藏", result.document?.folders?.single()?.name)
        assertTrue(server.takeRequest().path.orEmpty().contains("approot"))
        assertTrue(server.takeRequest().path.orEmpty().contains("content"))
    }

    @Test
    fun uploadSendsIfMatchAndReturnsNewEtag() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"eTag":"etag-2"}"""))

        val etag = client().uploadLibrary(document, "etag-1")

        assertEquals("etag-2", etag)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("etag-1", request.getHeader("If-Match"))
        assertTrue(request.path.orEmpty().contains("approot"))
    }

    @Test
    fun firstUploadUsesIfNoneMatch() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"eTag":"created"}"""))

        client().uploadLibrary(document, etag = null)

        assertEquals("*", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun uploadConflictIsReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(412))

        val error = runCatching { client().uploadLibrary(document, "stale") }.exceptionOrNull()

        assertTrue(error is SyncException.Conflict)
    }

    @Test
    fun unauthorizedTriggersSingleTokenRefresh() = runTest {
        var refreshes = 0
        val provider = AccessTokenProvider { forceRefresh ->
            if (forceRefresh) refreshes++
            if (refreshes == 0) "old" else "new"
        }
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"file-1","eTag":"etag"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(FavoriteSyncDocument.serializer(), document)),
        )

        client(provider).downloadLibrary()

        assertEquals(1, refreshes)
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun rateLimitUsesRetryAfterHeader() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "1"),
        )

        val error = runCatching { client().downloadLibrary() }.exceptionOrNull()

        assertTrue(error is SyncException.RateLimited)
        assertEquals(1_000L, (error as SyncException.RateLimited).retryAfterMs)
    }

    @Test
    fun serverErrorIsReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching { client().downloadLibrary() }.exceptionOrNull()

        assertTrue(error is SyncException.Server)
        assertEquals(503, (error as SyncException.Server).code)
    }
}
