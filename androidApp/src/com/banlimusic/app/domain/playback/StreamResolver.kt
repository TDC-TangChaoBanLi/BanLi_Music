package com.banlimusic.app.domain.playback

import androidx.core.net.toUri
import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TtlCache
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.network.NetworkMonitor
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.data.source.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class StreamResolver(
    private val providers: Map<String, MusicProvider>,
    private val settings: SettingsRepository,
    private val networkMonitor: NetworkMonitor? = null,
) {
    private val cache = TtlCache<String, StreamInfo>(maxEntries = 32, ttlMs = 5 * 60_000L)

    /** Tries every candidate source until one returns a URL (auto source switching). */
    suspend fun resolve(candidates: List<StreamCandidate>, qualityKbps: Int? = null): StreamInfo {
        if (candidates.isEmpty()) throw MusicException.NoPlayableSource()
        val current = settings.current()
        if (!current.allowMobileNetwork && networkMonitor?.isMetered() == true) {
            throw MusicException.Unavailable("已关闭移动网络播放，请连接 Wi-Fi 或在设置中允许移动网络")
        }
        // Playback retry handles failures; do not silently switch to another source.
        val effective = candidates.take(1)
        val quality = qualityKbps ?: current.qualityKbps
        val cacheKey = effective.joinToString(";") { "${it.providerId}:${it.providerTrackId}" } + ":$quality"
        cache.get(cacheKey)?.let { return it }

        var lastError: Throwable? = null
        for (candidate in effective) {
            val provider = providers[candidate.providerId] ?: continue
            try {
                val info = provider.resolveStream(candidate.providerTrackId, quality)
                cache.put(cacheKey, info)
                return info
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rateLimited: MusicException.RateLimited) {
                throw rateLimited
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw (lastError as? MusicException) ?: MusicException.NoPlayableSource()
    }

    /**
     * Called on ExoPlayer's loading thread. Resolution is deliberately blocking so
     * the data source can rewrite the URI before opening it.
     */
    fun resolveBlocking(candidates: List<StreamCandidate>): android.net.Uri {
        val info = runBlocking {
            withTimeout(30_000L) { resolve(candidates) }
        }
        return info.url.toUri()
    }

    fun clearCache() = cache.clear()
}
