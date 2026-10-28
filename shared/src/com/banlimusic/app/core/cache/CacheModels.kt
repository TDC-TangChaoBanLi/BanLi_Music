/**
 * 文件作用：定义两端共用的缓存统计与缓存歌曲模型。
 */
package com.banlimusic.app.core.cache

import com.banlimusic.app.core.model.Track

data class CacheUsage(
    val totalBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val artworkBytes: Long = 0L,
    val lyricsBytes: Long = 0L,
    val otherBytes: Long = 0L,
)

/**
 * One cached audio entry. Media3 stores cache keys, not metadata, so this shape
 * is shared even though only Android currently keeps a playback cache.
 */
data class CachedTrack(
    val cacheKey: String,
    val track: Track,
    val qualityKbps: Int,
    val cachedBytes: Long,
    val totalBytes: Long,
    val fileExtension: String,
    val createdAt: Long,
    val lastAccessAt: Long,
) {
    val isComplete: Boolean
        get() = totalBytes > 0L &&
            cachedBytes >= (totalBytes - totalBytes / 100L).coerceAtLeast(1L)
}
