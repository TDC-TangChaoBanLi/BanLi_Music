/**
 * 文件作用：封装 Media3 SimpleCache，提供播放缓存读取、删除与下载复用。
 */
package com.banlimusic.app.core.cache

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.domain.playback.StreamUriCodec

/**
 * Read access to the ExoPlayer media cache. Played audio stays on disk, so a later
 * download can copy the cached bytes instead of fetching the whole file again.
 */
@UnstableApi
class PlaybackCache(
    private val context: Context,
    val cache: SimpleCache,
) {

    fun cachedBytes(key: String): Long = runCatching {
        cache.getCachedBytes(key, 0L, Long.MAX_VALUE)
    }.getOrDefault(0L)

    fun isComplete(key: String, totalBytes: Long): Boolean {
        if (totalBytes <= 0L) return false
        val cached = cachedBytes(key)
        return cached > 0L && cached >= (totalBytes - totalBytes / 100L).coerceAtLeast(1L)
    }

    fun keys(): Set<String> = runCatching { cache.keys }.getOrDefault(emptySet())

    fun removeResource(key: String) {
        runCatching { cache.removeResource(key) }
    }

    /** Removes every cached quality of one application-level track. */
    fun removeTrack(trackId: String) {
        val prefix = StreamUriCodec.trackCachePrefix(trackId)
        keys().filter { it.startsWith(prefix) }.forEach(::removeResource)
    }

    fun removeAll() {
        keys().forEach(::removeResource)
    }

    fun dataSpec(url: String, key: String? = null): DataSpec =
        DataSpec.Builder()
            .setUri(url.toUri())
            .apply { if (!key.isNullOrBlank()) setKey(key) }
            .build()

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
