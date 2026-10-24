package com.banlimusic.app.data.source

import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.DownloadInfo
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.ProviderCapabilities
import com.banlimusic.app.core.model.SearchPage
import com.banlimusic.app.core.model.StreamInfo

/**
 * Every music backend implements this interface. The UI and player never talk to
 * a concrete provider, which keeps the app independent of GD Studio's API.
 */
interface MusicProvider {
    val providerId: String
    val displayName: String

    /** Backend-specific source key, e.g. netease / joox for a shared GD backend. */
    val sourceKey: String
    val capabilities: ProviderCapabilities

    suspend fun search(query: String, page: Int, pageSize: Int): SearchPage

    suspend fun resolveStream(trackId: String, qualityKbps: Int): StreamInfo

    suspend fun getLyrics(trackId: String, lyricKey: String?): Lyrics?

    suspend fun getArtworkUrl(artworkKey: String): String?

    suspend fun getDownloadInfo(trackId: String, qualityKbps: Int): DownloadInfo

    suspend fun rateLimitStatus(): RateLimitStatus? = null
}
