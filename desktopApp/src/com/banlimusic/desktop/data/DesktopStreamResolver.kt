package com.banlimusic.desktop.data

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TtlCache
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.data.source.MusicProvider
import com.banlimusic.app.domain.playback.StreamCandidate
import kotlinx.coroutines.CancellationException

/** Resolves a temporary stream URL through the shared provider limiters. */
class DesktopStreamResolver(
    private val providers: Map<String, MusicProvider>,
    private val settings: DesktopSettingsRepository,
) {
    private val cache = TtlCache<String, StreamInfo>(maxEntries = 32, ttlMs = 5 * 60_000L)

    suspend fun resolve(
        candidates: List<StreamCandidate>,
        qualityKbps: Int? = null,
    ): StreamInfo {
        if (candidates.isEmpty()) throw MusicException.NoPlayableSource()
        val effective = candidates.take(1)
        val quality = QualityTiers.normalize(qualityKbps ?: settings.current().qualityKbps)
        val cacheKey = effective.joinToString(";") {
            "${it.providerId}:${it.providerTrackId}"
        } + ":$quality"
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

    fun clear() = cache.clear()
}
