package com.banlimusic.app.data.source.gd

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.Album
import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.DownloadInfo
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.ProviderCapabilities
import com.banlimusic.app.core.model.SearchPage
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import com.banlimusic.app.data.source.MusicProvider
import kotlinx.serialization.json.JsonObject

/**
 * One GD Studio music source (netease, joox, bilibili...). Several instances
 * share a single [GdApiClient] because the published rate limit is per IP.
 */
class GdMusicProvider(
    private val client: GdApiClient,
    override val sourceKey: String,
    override val displayName: String,
) : MusicProvider {

    override val providerId: String = "gd-$sourceKey"

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        search = true,
        streaming = true,
        lyrics = true,
        download = true,
        downloadQualities = listOf(128, 192, 320, 740),
        artwork = true,
    )

    override suspend fun search(query: String, page: Int, pageSize: Int): SearchPage {
        val response = client.search(sourceKey, query, page, pageSize)
        val items = response.searchItems()
        val tracks = items.mapNotNull { it.toTrack() }
        return SearchPage(
            tracks = tracks,
            page = page,
            // The API does not expose a total; a full page means more may exist.
            hasMore = items.size >= pageSize,
        )
    }

    override suspend fun resolveStream(trackId: String, qualityKbps: Int): StreamInfo {
        val response = client.streamUrl(sourceKey, trackId, qualityKbps)
        val json = response as? JsonObject
            ?: throw MusicException.Parse()
        json.errorMessage()?.let { throw MusicException.Unavailable(it) }
        val url = json["url"].stringOrNull() ?: throw MusicException.Unavailable("该歌曲暂无可用播放地址")
        // The public documentation says KB, but the endpoint currently returns bytes.
        val sizeBytes = json["size"].longOrNull()
        return StreamInfo(
            url = url,
            bitrateKbps = json["br"].intOrNull() ?: qualityKbps,
            sizeBytes = sizeBytes,
        )
    }

    override suspend fun getLyrics(trackId: String, lyricKey: String?): Lyrics? {
        val response = client.lyrics(sourceKey, lyricKey ?: trackId)
        val json = response as? JsonObject ?: return null
        if (json["error"].stringOrNull() != null) return null
        val raw = json["lyric"].stringOrNull() ?: return null
        val translated = json["tlyric"].stringOrNull()
        if (raw.isBlank()) return null
        return Lyrics(raw = raw, translated = translated)
    }

    override suspend fun getArtworkUrl(artworkKey: String): String? {
        val response = client.artworkUrl(sourceKey, artworkKey)
        val json = response as? JsonObject ?: return null
        return json["url"].stringOrNull()
    }

    override suspend fun getDownloadInfo(trackId: String, qualityKbps: Int): DownloadInfo {
        val stream = resolveStream(trackId, qualityKbps)
        return DownloadInfo(
            url = stream.url,
            bitrateKbps = stream.bitrateKbps,
            sizeBytes = stream.sizeBytes,
        )
    }

    override suspend fun rateLimitStatus(): RateLimitStatus = client.rateLimitStatus()

    private fun JsonObject.toTrack(): Track? {
        val trackId = this["id"].stringOrNull() ?: return null
        val title = this["name"].stringOrNull() ?: "未知歌曲"
        val artistNames = this["artist"].stringList()
            .ifEmpty { this["artists"].stringList() }
            .flatMap { it.split('/', ',', '&', '、') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val albumTitle = this["album"].stringOrNull()
        val picId = this["pic_id"].stringOrNull()
        val lyricId = this["lyric_id"].stringOrNull()
        val durationSeconds = this["interval"].longOrNull() ?: this["duration"].longOrNull()
        val durationMs = durationSeconds?.let { if (it < 10_000L) it * 1000L else it }
        val explicit = this["explicit"].stringOrNull()?.equals("true", ignoreCase = true) == true

        return Track(
            id = "$providerId:$trackId",
            title = title,
            artists = artistNames.map { Artist(name = it) },
            album = albumTitle?.let { Album(title = it) },
            durationMs = durationMs,
            artworkUrl = null,
            sources = listOf(
                TrackSource(
                    providerId = providerId,
                    providerTrackId = trackId,
                    availableActions = setOf(TrackAction.STREAM, TrackAction.DOWNLOAD),
                    artworkKey = picId,
                    lyricKey = lyricId ?: trackId,
                    durationMs = durationMs,
                ),
            ),
            isExplicit = explicit,
        )
    }
}
