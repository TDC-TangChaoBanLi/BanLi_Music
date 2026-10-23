package com.banlimusic.app.data.repository

import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.DownloadInfo
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.ProviderInfo
import com.banlimusic.app.core.model.SearchPage
import com.banlimusic.app.core.model.StreamInfo
import com.banlimusic.app.core.model.Track

interface MusicRepository {
    suspend fun search(query: String, page: Int, pageSize: Int = 20): SearchPage

    suspend fun resolveStream(track: Track, qualityKbps: Int? = null): StreamInfo

    suspend fun lyrics(track: Track): Lyrics?

    suspend fun artwork(track: Track): String?

    suspend fun downloadInfo(track: Track, qualityKbps: Int? = null): DownloadInfo

    suspend fun providers(): List<ProviderInfo>

    suspend fun rateLimitStatus(): RateLimitStatus?

    fun clearCaches()
}
