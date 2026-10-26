/**
 * 文件作用：播放时懒解析临时流地址，并套用四档音质映射与限流。
 */
package com.banlimusic.app.domain.playback

import androidx.core.net.toUri
import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TtlCache
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.core.network.NetworkMonitor
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

    /**
     * Resolves a playable URL. With auto source switching enabled every
     * candidate is tried in order; [rotate] lets playback retries start with a
     * different source so a broken URL cannot trap the queue in a retry loop.
     */
    suspend fun resolve(
        candidates: List<StreamCandidate>,
        qualityKbps: Int? = null,
        rotate: Int = 0,
    ): StreamInfo {
        if (candidates.isEmpty()) throw MusicException.NoPlayableSource()
        val current = settings.current()
        if (!current.allowMobileNetwork && networkMonitor?.isMetered() == true) {
            throw MusicException.Unavailable("已关闭移动网络播放，请连接 Wi-Fi 或在设置中允许移动网络")
        }
        // When auto switching is disabled the retry rotation is ignored so the
        // preferred source is always used, exactly as the setting promises.
        val effective = if (current.autoSwitchPlaybackSource) {
            SourceOrdering.rotate(candidates, rotate)
        } else {
            candidates.take(1)
        }
        val quality = QualityTiers.normalize(qualityKbps ?: current.qualityKbps)
        // The key follows the first candidate: switching the preferred source
        // produces a new key, while retrying the same one reuses the cache.
        val cacheKey = "${effective.first().providerId}:${effective.first().providerTrackId}:$quality"
        cache.get(cacheKey)?.let { return it }

        var lastError: Throwable? = null
        for (candidate in effective) {
            val provider = providers[candidate.providerId] ?: continue
            val providerQuality = QualityTiers.mapToProvider(
                requestedKbps = quality,
                providerQualities = provider.capabilities.downloadQualities,
            )
            try {
                val info = provider.resolveStream(candidate.providerTrackId, providerQuality)
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

    data class ResolvedStream(
        val info: StreamInfo,
        val qualityKbps: Int,
    )

    /**
     * Called on ExoPlayer's loading thread. Resolution is deliberately blocking so
     * the data source can rewrite the URI before opening it.
     */
    fun resolveBlocking(
        candidates: List<StreamCandidate>,
        qualityKbps: Int? = null,
        rotate: Int = 0,
    ): android.net.Uri = resolveBlockingInfo(candidates, qualityKbps, rotate).info.url.toUri()

    fun resolveBlockingInfo(
        candidates: List<StreamCandidate>,
        qualityKbps: Int? = null,
        rotate: Int = 0,
    ): ResolvedStream = runBlocking {
        val quality = QualityTiers.normalize(qualityKbps ?: settings.current().qualityKbps)
        val info = withTimeout(30_000L) { resolve(candidates, quality, rotate) }
        ResolvedStream(info = info, qualityKbps = info.bitrateKbps ?: quality)
    }

    /** Drops cached URLs before a retry so an expired URL is not reused. */
    fun invalidate(candidates: List<StreamCandidate>, qualityKbps: Int? = null) {
        if (candidates.isEmpty()) return
        val quality = QualityTiers.normalize(
            qualityKbps ?: runBlocking { settings.current().qualityKbps },
        )
        candidates.forEach { candidate ->
            cache.remove("${candidate.providerId}:${candidate.providerTrackId}:$quality")
        }
    }

    fun clearCache() = cache.clear()
}
