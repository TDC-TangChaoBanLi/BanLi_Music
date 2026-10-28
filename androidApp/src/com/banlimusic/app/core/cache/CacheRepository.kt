/**
 * 文件作用：维护 Media3 播放缓存索引，提供缓存歌曲查询、删除与转下载。
 */
package com.banlimusic.app.core.cache

import com.banlimusic.app.core.database.dao.CacheDao
import com.banlimusic.app.core.database.entity.CachedTrackEntity
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Application-level index for Media3's playback cache. Media3 stores cache keys,
 * not track metadata, so this repository records which track/quality each stable
 * cache key belongs to and turns cached audio into normal downloads.
 */
class CacheRepository(
    private val dao: CacheDao,
    private val json: Json,
    private val playbackCache: PlaybackCache,
    private val cacheManager: CacheManager,
    private val downloads: DownloadRepository,
) {

    fun observeCachedTracks(): Flow<List<CachedTrack>> = dao.observeAll().map { entities ->
        withContext(Dispatchers.IO) {
            entities.mapNotNull(::toCachedTrack)
        }
    }

    suspend fun refreshSizes() = withContext(Dispatchers.IO) {
        dao.getAllOldestFirst().forEach { entity ->
            val bytes = playbackCache.cachedBytes(entity.cacheKey)
            if (bytes <= 0L) {
                dao.delete(entity.cacheKey)
            } else {
                dao.updateSize(entity.cacheKey, entity.totalBytes)
            }
        }
    }

    suspend fun record(
        track: Track,
        cacheKey: String,
        qualityKbps: Int,
        totalBytes: Long,
        sourceUrl: String?,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.getByKey(cacheKey)
        dao.upsert(
            CachedTrackEntity(
                cacheKey = cacheKey,
                trackId = track.id,
                trackJson = json.encodeToString(Track.serializer(), track),
                qualityKbps = qualityKbps,
                // A pre-created index entry has no size yet; never overwrite a
                // size learned from an earlier resolved stream.
                totalBytes = maxOf(totalBytes, existing?.totalBytes ?: 0L),
                fileExtension = if (sourceUrl != null) {
                    extensionFor(sourceUrl)
                } else {
                    existing?.fileExtension ?: "mp3"
                },
                createdAt = existing?.createdAt ?: now,
                lastAccessAt = now,
            ),
        )
    }

    suspend fun findForTrack(trackId: String, qualityKbps: Int): CachedTrack? =
        withContext(Dispatchers.IO) {
            dao.getForTrackAndQuality(trackId, qualityKbps)?.let(::toCachedTrack)
        }

    suspend fun removeTrack(trackId: String) = withContext(Dispatchers.IO) {
        playbackCache.removeTrack(trackId)
        dao.deleteByTrackId(trackId)
    }

    suspend fun remove(cacheKey: String) = withContext(Dispatchers.IO) {
        playbackCache.removeResource(cacheKey)
        dao.delete(cacheKey)
    }

    suspend fun clearAudio() = withContext(Dispatchers.IO) {
        playbackCache.removeAll()
        dao.clear()
    }

    suspend fun clearArtwork() = withContext(Dispatchers.IO) { cacheManager.clearArtwork() }

    suspend fun clearLyrics() = withContext(Dispatchers.IO) { cacheManager.clearLyrics() }

    suspend fun clearOther() = withContext(Dispatchers.IO) { cacheManager.clearOther() }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        playbackCache.removeAll()
        dao.clear()
        cacheManager.clearNonAudio()
    }

    suspend fun usage(): CacheUsage = withContext(Dispatchers.IO) { cacheManager.usage() }

    suspend fun trimTo(maxBytes: Long) = withContext(Dispatchers.IO) {
        cacheManager.trimNonAudioTo(maxBytes)
        var usage = cacheManager.usage()
        if (usage.totalBytes <= maxBytes) return@withContext
        for (entity in dao.getAllOldestFirst()) {
            if (usage.totalBytes <= maxBytes) break
            playbackCache.removeResource(entity.cacheKey)
            dao.delete(entity.cacheKey)
            usage = cacheManager.usage()
        }
    }

    /**
     * Queues one download per cached track. Tracks that are already downloaded
     * are removed from the playback cache immediately; the worker removes the
     * cache of the others after a successful download.
     */
    suspend fun convertToDownloads(cached: Collection<CachedTrack>): Int = withContext(Dispatchers.IO) {
        var queued = 0
        cached.distinctBy { it.track.id }.forEach { entry ->
            val result = downloads.enqueue(entry.track, entry.qualityKbps.takeIf { it > 0 })
            if (result.isSuccess) queued++
            if (downloads.hasCompleted(entry.track.id)) removeTrack(entry.track.id)
        }
        queued
    }

    private fun toCachedTrack(entity: CachedTrackEntity): CachedTrack? {
        val track = runCatching {
            json.decodeFromString(Track.serializer(), entity.trackJson)
        }.getOrNull() ?: return null
        val bytes = playbackCache.cachedBytes(entity.cacheKey)
        if (bytes <= 0L) return null
        return CachedTrack(
            cacheKey = entity.cacheKey,
            track = track,
            qualityKbps = entity.qualityKbps,
            cachedBytes = bytes,
            totalBytes = entity.totalBytes,
            fileExtension = entity.fileExtension,
            createdAt = entity.createdAt,
            lastAccessAt = entity.lastAccessAt,
        )
    }

    private fun extensionFor(url: String?): String {
        val path = url
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.lowercase()
            .orEmpty()
        val extension = path.substringAfterLast('.', "")
        return extension.takeIf { it in AUDIO_EXTENSIONS } ?: "mp3"
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }
}
