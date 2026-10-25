package com.banlimusic.desktop

import com.banlimusic.app.core.datastore.MAX_API_LIMIT_PER_WINDOW
import com.banlimusic.app.core.datastore.MIN_GD_REQUEST_INTERVAL_MS
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.limiter.SlidingWindowRateLimiter
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.data.source.MusicProvider
import com.banlimusic.app.data.source.gd.GdApiClient
import com.banlimusic.app.data.source.gd.GdMusicProvider
import com.banlimusic.app.domain.search.TrackMerger
import com.banlimusic.desktop.data.DesktopDownloadManager
import com.banlimusic.desktop.data.DesktopLibraryRepository
import com.banlimusic.desktop.data.DesktopMetadataCache
import com.banlimusic.desktop.data.DesktopMusicRepository
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.desktop.data.DesktopStreamResolver
import com.banlimusic.desktop.player.DesktopAudioPlayer
import com.banlimusic.desktop.ui.DesktopArtworkLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency graph for the desktop client, mirroring Android's container. */
class DesktopContainer {

    init {
        DesktopPaths.ensureDirectories()
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    val settingsRepository = DesktopSettingsRepository()

    val mediaHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val rateLimiter = SlidingWindowRateLimiter(
        limit = MAX_API_LIMIT_PER_WINDOW,
        windowMs = 5 * 60_000L,
        maxWaitMs = 12_000L,
        minIntervalMs = MIN_GD_REQUEST_INTERVAL_MS,
        limitProvider = { settingsRepository.current().apiLimitPerWindow },
    )

    private val requestScheduler = RequestScheduler(
        maxConcurrent = 2,
        limiter = rateLimiter,
    )

    private val jsonHttpClient = JsonHttpClient(
        client = JsonHttpClient.createClient(DesktopPaths.cacheDir.toFile()),
        json = json,
        scheduler = requestScheduler,
    )

    private val gdApiClient = GdApiClient(jsonHttpClient, requestScheduler)

    val providers: List<MusicProvider> = SUPPORTED_SOURCES
        .map { source -> GdMusicProvider(gdApiClient, source.id, source.label) }

    val metadataCache = DesktopMetadataCache(
        root = DesktopPaths.cacheDir.toFile(),
        json = json,
        httpClient = mediaHttpClient,
    )

    private val streamResolver = DesktopStreamResolver(
        providers = providers.associateBy { it.providerId },
        settings = settingsRepository,
    )

    val musicRepository = DesktopMusicRepository(
        providers = providers,
        settings = settingsRepository,
        merger = TrackMerger(),
        streamResolver = streamResolver,
        metadataCache = metadataCache,
    )

    val libraryRepository = DesktopLibraryRepository(
        file = DesktopPaths.libraryFile,
        json = json,
    )

    val artworkLoader = DesktopArtworkLoader(
        repository = musicRepository,
        metadataCache = metadataCache,
        httpClient = mediaHttpClient,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads = DesktopDownloadManager(
        file = DesktopPaths.configDir.resolve("downloads.json"),
        json = json,
        musicRepository = musicRepository,
        settings = settingsRepository,
        scope = scope,
        httpClient = mediaHttpClient,
    )

    val player = DesktopAudioPlayer(
        scope = scope,
        repository = musicRepository,
        httpClient = mediaHttpClient,
    )

    fun close() {
        downloads.cancelAll()
        player.shutdown()
        scope.cancel()
        runCatching { mediaHttpClient.dispatcher.executorService.shutdown() }
        runCatching { mediaHttpClient.connectionPool.evictAll() }
    }
}
