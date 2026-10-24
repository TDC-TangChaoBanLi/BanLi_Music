package com.banlimusic.app.core.cache

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import com.banlimusic.app.core.network.JsonHttpClient

/**
 * Read access to the ExoPlayer media cache. Played audio stays on disk, so a later
 * download can copy the cached bytes instead of fetching the whole file again.
 */
@UnstableApi
class PlaybackCache(
    private val context: Context,
    val cache: SimpleCache,
) {

    /** Number of contiguous bytes cached from the start of [url]. */
    fun cachedBytes(url: String): Long = runCatching {
        val key = CacheKeyFactory.DEFAULT.buildCacheKey(DataSpec(url.toUri()))
        cache.getCachedBytes(key, 0L, Long.MAX_VALUE)
    }.getOrDefault(0L)

    fun dataSpec(url: String): DataSpec = DataSpec(url.toUri())

    /**
     * Reads [url] through the cache: cached spans are served locally, missing
     * ranges fall back to the network. Used by downloads to convert a cached
     * stream into a saved file.
     */
    fun readSource(): DataSource = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(
            DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory()
                    .setUserAgent(JsonHttpClient.USER_AGENT)
                    .setAllowCrossProtocolRedirects(true),
            ),
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .createDataSource()
}
