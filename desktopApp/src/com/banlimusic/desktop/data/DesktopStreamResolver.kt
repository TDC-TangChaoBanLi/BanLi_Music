package com.banlimusic.desktop.data

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TtlCache
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.data.source.MusicProvider
import com.banlimusic.app.domain.playback.SourceOrdering
import com.banlimusic.app.domain.playback.StreamCandidate
import kotlinx.coroutines.CancellationException

/** Resolves a temporary stream URL through the shared provider limiters. */
class DesktopStreamResolver(
    private val providers: Map<String, MusicProvider>,
    private val settings: DesktopSettingsRepository,
) {
    private val cache = TtlCache<String, StreamInfo>(maxEntries = 32, ttlMs = 5 * 60_000L)

    /**
     * Resolves a playable URL. Retries rotate the candidate order so a broken
     * source cannot trap playback on the same dead URL forever.
     */
    suspend fun resolve(
        candidates: List<StreamCandidate>,
        qualityKbps: Int? = null,
        rotate: Int = 0,
    ): StreamInfo {
        if (candidates.isEmpty()) throw MusicException.NoPlayableSource()
        val current = settings.current()
        val effective = if (current.autoSwitchPlaybackSource) {
            SourceOrdering.rotate(candidates, rotate)
        } else {
            candidates.take(1)
        }
        val quality = QualityTiers.normalize(qualityKbps ?: current.qualityKbps)
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

    /** Drops cached URLs before a retry so an expired URL is not reused. */
    fun invalidate(candidates: List<StreamCandidate>, qualityKbps: Int? = null) {
        if (candidates.isEmpty()) return
        val quality = QualityTiers.normalize(
            qualityKbps ?: settings.current().qualityKbps,
        )
        candidates.forEach { candidate ->
            cache.remove("${candidate.providerId}:${candidate.providerTrackId}:$quality")
        }
    }

    fun clear() = cache.clear()
}
