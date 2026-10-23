package com.banlimusic.app

import android.content.Context
import com.banlimusic.app.core.database.MusicDatabase
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.limiter.RequestScheduler
import com.banlimusic.app.core.limiter.SlidingWindowRateLimiter
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.core.network.NetworkMonitor
import com.banlimusic.app.data.repository.DefaultMusicRepository
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.data.source.MusicProvider
import com.banlimusic.app.data.source.gd.GdApiClient
import com.banlimusic.app.data.source.gd.GdMusicProvider
import com.banlimusic.app.domain.playback.StreamResolver
import com.banlimusic.app.domain.search.TrackMerger
import com.banlimusic.app.player.PlaybackController
import kotlinx.serialization.json.Json

/**
 * Manual dependency container. Keeping construction explicit makes the ownership
 * of rate limiting, caches and the Media3 connection obvious.
 */
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

    // GD Studio documents a limit of 50 requests per 5 minutes per IP.
    private val rateLimiter by lazy {
        SlidingWindowRateLimiter(limit = 50, windowMs = 5 * 60_000L, maxWaitMs = 8_000L)
    }
    private val requestScheduler by lazy { RequestScheduler(maxConcurrent = 2, limiter = rateLimiter) }
    private val httpClient by lazy { JsonHttpClient(okHttpClient, json, requestScheduler) }
    private val gdApiClient by lazy { GdApiClient(httpClient, requestScheduler) }

    val providers: List<MusicProvider> by lazy {
        listOf(
            GdMusicProvider(gdApiClient, "netease", "网易云音乐"),
            GdMusicProvider(gdApiClient, "joox", "JOOX"),
            GdMusicProvider(gdApiClient, "bilibili", "哔哩哔哩"),
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
        DefaultMusicRepository(providers, settingsRepository, trackMerger, streamResolver)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database.libraryDao(), json)
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(context, database.downloadDao(), json, settingsRepository)
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(context, json)
    }
}
