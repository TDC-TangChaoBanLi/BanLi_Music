package com.banlimusic.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.banlimusic.app.core.cache.CacheManager
import com.banlimusic.app.core.cache.MediaMetadataCache
import com.banlimusic.app.core.cache.PlaybackCache
import com.banlimusic.app.core.database.MusicDatabase
import com.banlimusic.app.core.datastore.MAX_API_LIMIT_PER_WINDOW
import com.banlimusic.app.core.datastore.MIN_GD_REQUEST_INTERVAL_MS
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.limiter.SlidingWindowRateLimiter
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.core.network.NetworkMonitor
import com.banlimusic.app.data.repository.DefaultMusicRepository
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.LocalMusicRepository
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.data.source.MusicProvider
import com.banlimusic.app.data.source.gd.GdApiClient
import com.banlimusic.app.data.source.gd.GdMusicProvider
import com.banlimusic.app.domain.playback.StreamResolver
import com.banlimusic.app.domain.search.TrackMerger
import com.banlimusic.app.player.PlaybackController
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manual dependency container. Keeping construction explicit makes the ownership
 * of rate limiting, caches and the Media3 connection obvious.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class AppContainer(private val context: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val database: MusicDatabase by lazy { MusicDatabase.get(context) }

    private val okHttpClient by lazy { JsonHttpClient.createClient(context.cacheDir) }

    val downloadHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }

    // GD Studio documents a limit of 50 requests per 5 minutes per IP. The user
    // can lower it at runtime, and every request is spaced by at least 1 second.
    private val rateLimiter by lazy {
        SlidingWindowRateLimiter(
            limit = MAX_API_LIMIT_PER_WINDOW,
            windowMs = 5 * 60_000L,
            maxWaitMs = 12_000L,
            minIntervalMs = MIN_GD_REQUEST_INTERVAL_MS,
            limitProvider = { settingsRepository.current().apiLimitPerWindow },
        )
    }
    private val requestScheduler by lazy { RequestScheduler(maxConcurrent = 2, limiter = rateLimiter) }
    private val httpClient by lazy { JsonHttpClient(okHttpClient, json, requestScheduler) }
    private val gdApiClient by lazy { GdApiClient(httpClient, requestScheduler) }

    val providers: List<MusicProvider> by lazy {
        listOf(
            GdMusicProvider(gdApiClient, "netease", "GD网易云"),
            GdMusicProvider(gdApiClient, "joox", "GD JOOX"),
            GdMusicProvider(gdApiClient, "bilibili", "GD B站"),
        )
    }

    val trackMerger: TrackMerger by lazy { TrackMerger() }

    private val networkMonitor by lazy { NetworkMonitor(context) }

    val streamResolver: StreamResolver by lazy {
        StreamResolver(
            providers = providers.associateBy { it.providerId },
            settings = settingsRepository,
            networkMonitor = networkMonitor,
        )
    }

    val musicRepository: MusicRepository by lazy {
        DefaultMusicRepository(
            providers = providers,
            settings = settingsRepository,
            merger = trackMerger,
            streamResolver = streamResolver,
            metadataCache = mediaMetadataCache,
        )
    }

    val mediaMetadataCache: MediaMetadataCache by lazy {
        MediaMetadataCache(context, json, downloadHttpClient)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database.libraryDao(), json)
    }

    val localMusicRepository: LocalMusicRepository by lazy {
        LocalMusicRepository(context, database.localMusicDao(), json)
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(context, database.downloadDao(), json, settingsRepository)
    }

    val cacheManager: CacheManager by lazy { CacheManager(context) }

    /** ExoPlayer writes streamed audio here so downloads can reuse it. */
    val playbackCache: PlaybackCache by lazy {
        PlaybackCache(
            context,
            SimpleCache(
                File(context.cacheDir, "playback-media"),
                LeastRecentlyUsedCacheEvictor(PLAYBACK_CACHE_MAX_BYTES),
                StandaloneDatabaseProvider(context),
            ),
        )
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(context, json, settingsRepository, downloadRepository)
    }

    private companion object {
        const val PLAYBACK_CACHE_MAX_BYTES = 256L * 1024 * 1024
    }
}
