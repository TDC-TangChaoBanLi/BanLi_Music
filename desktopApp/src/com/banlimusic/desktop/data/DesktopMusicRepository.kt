package com.banlimusic.desktop.data

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TtlCache
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.DownloadInfo
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.ProviderInfo
import com.banlimusic.app.core.model.SearchPage
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.data.source.MusicProvider
import com.banlimusic.app.domain.playback.StreamCandidate
import com.banlimusic.app.domain.search.TrackMerger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/** Desktop implementation of the shared music facade. */
class DesktopMusicRepository(
    private val providers: List<MusicProvider>,
    private val settings: DesktopSettingsRepository,
    private val merger: TrackMerger,
    private val streamResolver: DesktopStreamResolver,
    private val metadataCache: DesktopMetadataCache,
) : MusicRepository {

    private val providersById = providers.associateBy { it.providerId }
    private val searchCache = TtlCache<String, SearchPage>(maxEntries = 24, ttlMs = 5 * 60_000L)
    private val artworkCache = TtlCache<String, String>(maxEntries = 128, ttlMs = 6 * 60 * 60_000L)
    private val lyricsCache = TtlCache<String, Lyrics>(maxEntries = 32, ttlMs = 12 * 60 * 60_000L)

    override suspend fun search(query: String, page: Int, pageSize: Int): SearchPage {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchPage(page = page)

        val current = settings.current()
        val active = providers.filter {
            it.sourceKey in current.enabledSources && it.capabilities.search
        }
        if (active.isEmpty()) {
            throw MusicException.Unavailable("请先在设置中启用至少一个音乐源")
        }

        val cacheKey = active.map { it.providerId }.sorted().joinToString(",") +
            "|$trimmed|$page|$pageSize"
        searchCache.get(cacheKey)?.let { return it }

        val results = supervisorScope {
            active.map { provider ->
                async {
                    val result = try {
                        Result.success(provider.search(trimmed, page, pageSize))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    provider to result
                }
            }.map { it.await() }
        }

        val successful = results.mapNotNull { (provider, result) ->
            result.getOrNull()?.let { provider to it }
        }
        if (successful.isEmpty()) {
            val error = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            throw (error as? MusicException) ?: MusicException.Network(error)
        }

        val merged = merger.merge(successful.flatMap { it.second.tracks })
            .sortedWith(
                compareByDescending<Track> { merger.relevance(it, trimmed) }
                    .thenBy { it.title.lowercase() },
            )

        val result = SearchPage(
            tracks = merged,
            page = page,
            hasMore = successful.any { it.second.hasMore },
        )
        searchCache.put(cacheKey, result)
        return result
    }

    override suspend fun resolveStream(track: Track, qualityKbps: Int?): StreamInfo {
        val candidates = track.sources
            .filter { TrackAction.STREAM in it.availableActions }
            .map { StreamCandidate(it.providerId, it.providerTrackId) }
        return streamResolver.resolve(candidates, qualityKbps)
    }

    override suspend fun lyrics(track: Track): Lyrics? {
        lyricsCache.get(track.id)?.let { return it }
        metadataCache.lyrics(track.id)?.let { cached ->
            lyricsCache.put(track.id, cached)
            return cached
        }
        for (source in track.sources) {
            val provider = providersById[source.providerId] ?: continue
            if (!provider.capabilities.lyrics) continue
            try {
                val lyrics = provider.getLyrics(source.providerTrackId, source.lyricKey)
                if (lyrics != null && !lyrics.isEmpty) {
                    lyricsCache.put(track.id, lyrics)
                    metadataCache.putLyrics(track.id, lyrics)
                    return lyrics
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rateLimited: MusicException.RateLimited) {
                throw rateLimited
            } catch (_: Throwable) {
                // Try the next source.
            }
        }
        return null
    }

    override suspend fun artwork(track: Track): String? {
        metadataCache.artwork(track.id)?.let { cached ->
            artworkCache.put(track.id, cached)
            return cached
        }
        artworkCache.get(track.id)?.let { return it }
        track.artworkUrl?.let { url ->
            metadataCache.putArtwork(track.id, url)?.let { local ->
                artworkCache.put(track.id, local)
                return local
            }
            return url
        }

        for (source in track.sources) {
            val provider = providersById[source.providerId] ?: continue
            val key = source.artworkKey ?: continue
            if (!provider.capabilities.artwork) continue
            try {
                val url = provider.getArtworkUrl(key) ?: continue
                val resolved = metadataCache.putArtwork(track.id, url) ?: url
                artworkCache.put(track.id, resolved)
                return resolved
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rateLimited: MusicException.RateLimited) {
                throw rateLimited
            } catch (_: Throwable) {
                // Try the next source.
            }
        }
        return null
    }

    override suspend fun downloadInfo(track: Track, qualityKbps: Int?): DownloadInfo {
        val stream = resolveStream(track, qualityKbps)
        return DownloadInfo(
            url = stream.url,
            bitrateKbps = stream.bitrateKbps,
            sizeBytes = stream.sizeBytes,
        )
    }

    override suspend fun downloadQualities(tracks: Collection<Track>): List<Int> {
        if (tracks.isEmpty()) return emptyList()
        val providersForTracks = tracks.map { track ->
            track.sources.firstOrNull { TrackAction.DOWNLOAD in it.availableActions }
                ?.let { providersById[it.providerId] }
                ?: track.sources.firstOrNull { TrackAction.STREAM in it.availableActions }
                    ?.let { providersById[it.providerId] }
        }
        if (providersForTracks.any { it == null }) return emptyList()
        val qualities = providersForTracks.filterNotNull()
            .map { it.capabilities.downloadQualities }
        if (qualities.any { it.isEmpty() }) return emptyList()
        return qualities
            .map { it.toSet() }
            .reduce { common, next -> common.intersect(next) }
            .sorted()
    }

    override suspend fun providers(): List<ProviderInfo> {
        val enabled = settings.current().enabledSources
        return providers.map { provider ->
            ProviderInfo(
                id = provider.providerId,
                name = provider.displayName,
                enabled = provider.sourceKey in enabled,
            )
        }
    }

    override suspend fun rateLimitStatus(): RateLimitStatus? =
        providers.firstNotNullOfOrNull { provider ->
            try {
                provider.rateLimitStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }

    override fun clearCaches() {
        searchCache.clear()
        artworkCache.clear()
        lyricsCache.clear()
        streamResolver.clear()
    }
}
