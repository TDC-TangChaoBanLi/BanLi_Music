/**
 * 文件作用：持久化下载任务并调度 WorkManager，管理暂停、恢复与文件落盘。
 */
package com.banlimusic.app.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.net.toUri
import com.banlimusic.app.core.cache.PlaybackCache
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.database.dao.CacheDao
import com.banlimusic.app.core.database.dao.DownloadDao
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.domain.download.DownloadStatus
import com.banlimusic.app.domain.download.DownloadTarget
import com.banlimusic.app.domain.download.DownloadTask
import com.banlimusic.app.domain.download.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val cacheDao: CacheDao,
    private val playbackCache: PlaybackCache,
    private val json: Json,
    private val settings: SettingsRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {

    fun observeTasks(): Flow<List<DownloadTask>> = dao.observeAll().map { entities ->
        entities.mapNotNull(::toTask)
    }

    suspend fun completedBytes(): Long = dao.completedBytes()

    suspend fun hasCompleted(trackId: String): Boolean =
        dao.getCompletedForTrack(trackId).any { locationExists(it.filePath) }

    suspend fun enqueue(
        track: Track,
        qualityKbps: Int? = null,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
    ): Result<Unit> = runCatching {
        val current = settings.current()
        val source = track.sources.firstOrNull { TrackAction.DOWNLOAD in it.availableActions }
            ?: track.sources.firstOrNull { TrackAction.STREAM in it.availableActions }
            ?: throw IllegalStateException("没有可下载的来源")
        val quality = qualityKbps
            ?: current.downloadQualityKbps.takeIf { it > 0 }
            ?: current.qualityKbps
        val lyrics = downloadLyrics ?: current.downloadLyrics
        val artwork = downloadArtwork ?: current.downloadArtwork
        val taskId = taskId(track.id, source.providerId, quality, lyrics, artwork)
        val existing = dao.getById(taskId)
        if (existing != null && existing.status == DownloadStatus.COMPLETED.name) {
            if (locationExists(existing.filePath)) {
                clearCachedCopy(track.id)
                return@runCatching
            }
        }

        // A finished copy of the same track exists (maybe at another quality).
        // Reuse that file instead of downloading the audio a second time.
        val reusable = dao.getCompletedForTrack(track.id)
            .firstOrNull { it.id != taskId && locationExists(it.filePath) }
        if (reusable != null) {
            val now = System.currentTimeMillis()
            dao.upsert(
                DownloadTaskEntity(
                    id = taskId,
                    trackJson = json.encodeToString(Track.serializer(), track),
                    title = track.title,
                    artist = track.artistLine,
                    providerId = source.providerId,
                    providerTrackId = source.providerTrackId,
                    status = DownloadStatus.COMPLETED.name,
                    bytesDownloaded = reusable.bytesDownloaded,
                    totalBytes = reusable.totalBytes,
                    filePath = reusable.filePath,
                    error = null,
                    retryCount = 0,
                    createdAt = now,
                    updatedAt = now,
                    trackId = track.id,
                    qualityKbps = quality,
                    downloadLyrics = lyrics,
                    downloadArtwork = artwork,
                ),
            )
            clearCachedCopy(track.id)
            return@runCatching
        }

        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadTaskEntity(
                id = taskId,
                trackJson = json.encodeToString(Track.serializer(), track),
                title = track.title,
                artist = track.artistLine,
                providerId = source.providerId,
                providerTrackId = source.providerTrackId,
                status = DownloadStatus.QUEUED.name,
                bytesDownloaded = existing?.bytesDownloaded ?: 0L,
                totalBytes = existing?.totalBytes ?: 0L,
                filePath = existing?.filePath,
                error = null,
                retryCount = existing?.retryCount ?: 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                trackId = track.id,
                qualityKbps = quality,
                downloadLyrics = lyrics,
                downloadArtwork = artwork,
            ),
        )
        schedule(taskId, ExistingWorkPolicy.KEEP)
    }

    /**
     * Queues multiple downloads through the same WorkManager pipeline. API calls
     * are still serialized by the shared rate limiter, so batch operations cannot
     * exceed the configured GD Music request budget.
     */
    suspend fun enqueueAll(
        tracks: Collection<Track>,
        qualityKbps: Int? = null,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
    ): Int {
        var queued = 0
        tracks.distinctBy { it.id }.forEach { track ->
            if (enqueue(track, qualityKbps, downloadLyrics, downloadArtwork).isSuccess) {
                queued++
            }
        }
        return queued
    }

    suspend fun pause(id: String) {
        workManager.cancelUniqueWork(workName(id))
        val entity = dao.getById(id) ?: return
        if (entity.status == DownloadStatus.COMPLETED.name) return
        dao.updateStatus(
            id = id,
            status = DownloadStatus.PAUSED.name,
            error = null,
            filePath = entity.filePath,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun resume(id: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status == DownloadStatus.COMPLETED.name) return
        dao.updateStatus(
            id = id,
            status = DownloadStatus.QUEUED.name,
            error = null,
            filePath = entity.filePath,
            updatedAt = System.currentTimeMillis(),
        )
        schedule(id, ExistingWorkPolicy.REPLACE)
    }

    suspend fun cancel(id: String) {
        workManager.cancelUniqueWork(workName(id))
        val entity = dao.getById(id) ?: return
        deleteLocation(entity.filePath)
        deleteTemp(id)
        dao.updateStatus(
            id = id,
            status = DownloadStatus.CANCELLED.name,
            error = null,
            filePath = null,
            updatedAt = System.currentTimeMillis(),
        )
        dao.updateProgress(id, 0L, 0L, System.currentTimeMillis())
    }

    suspend fun remove(id: String) {
        workManager.cancelUniqueWork(workName(id))
        val entity = dao.getById(id) ?: return
        deleteLocation(entity.filePath)
        deleteTemp(id)
        dao.delete(id)
    }

    suspend fun clearCompleted() {
        val completed = dao.getCompleted()
        completed.forEach { entity -> deleteLocation(entity.filePath) }
        dao.deleteCompleted()
    }

    suspend fun getEntity(id: String): DownloadTaskEntity? = dao.getById(id)

    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        error: String? = null,
        filePath: String? = null,
    ) {
        dao.updateStatus(
            id = id,
            status = status.name,
            error = error,
            filePath = filePath ?: dao.getById(id)?.filePath,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun updateProgress(id: String, bytes: Long, total: Long) {
        dao.updateProgress(id, bytes, total, System.currentTimeMillis())
    }

    suspend fun updateRetryCount(id: String, retryCount: Int) {
        dao.updateRetryCount(id, retryCount, System.currentTimeMillis())
    }

    fun decodeTrack(entity: DownloadTaskEntity): Track? = runCatching {
        json.decodeFromString(Track.serializer(), entity.trackJson)
    }.getOrNull()

    /** Returns completed local media URIs for the given application-level track ids. */
    suspend fun completedUris(trackIds: Collection<String>): Map<String, Uri> {
        val wanted = trackIds.toSet()
        if (wanted.isEmpty()) return emptyMap()
        return dao.getCompleted()
            .asSequence()
            .filter { entity ->
                val id = entity.trackId.ifBlank { entity.id.substringBeforeLast('@') }
                id in wanted
            }
            .mapNotNull { entity ->
                val path = entity.filePath ?: return@mapNotNull null
                val id = entity.trackId.ifBlank { entity.id.substringBeforeLast('@') }
                id to path.toUri()
            }
            .toMap()
    }

    /**
     * On Android 10+ the default target is MediaStore's Music/BanLiMusic folder,
     * which does not require broad storage permissions. A writable custom folder
     * takes precedence; older devices fall back to a file directory.
     */
    suspend fun downloadTarget(): DownloadTarget {
        val configured = settings.current().downloadDirectory.trim()
        if (configured.isNotEmpty()) {
            val directory = File(configured)
            if (isWritableDirectory(directory)) return DownloadTarget.Directory(directory)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return DownloadTarget.MediaStoreMusic
        }

        val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val publicDirectory = File(publicMusic, "BanLiMusic")
        if (isWritableDirectory(publicDirectory)) return DownloadTarget.Directory(publicDirectory)

        val fallbackRoot = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        return DownloadTarget.Directory(File(fallbackRoot, "BanLiMusic").apply { mkdirs() })
    }

    private fun isWritableDirectory(directory: File): Boolean {
        if (!(directory.exists() || directory.mkdirs())) return false
        if (!directory.canWrite()) return false
        return runCatching {
            val probe = File(directory, ".banli_write_test")
            probe.createNewFile() && probe.delete()
        }.getOrDefault(false)
    }

    private suspend fun clearCachedCopy(trackId: String) {
        playbackCache.removeTrack(trackId)
        cacheDao.deleteByTrackId(trackId)
    }

    private fun locationExists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (path.startsWith("content://")) return true
        return File(path).exists()
    }

    private fun deleteLocation(path: String?) {
        if (path.isNullOrBlank()) return
        if (path.startsWith("content://")) {
            runCatching { context.contentResolver.delete(Uri.parse(path), null, null) }
        } else {
            runCatching { File(path).delete() }
        }
    }

    private fun deleteTemp(taskId: String) {
        val temp = File(
            context.cacheDir,
            "downloads/${TextNormalizer.sanitizeFileName(taskId)}.part",
        )
        runCatching { temp.delete() }
    }

    fun suggestedFileName(track: Track, url: String, taskId: String): String {
        val extension = extensionFor(url)
        val base = TextNormalizer.sanitizeFileName("${track.title} - ${track.artistLine}")
        val suffix = (taskId.hashCode() and 0xFFFFFF).toString(16).padStart(6, '0')
        return "$base-$suffix.$extension"
    }

    private suspend fun schedule(id: String, policy: ExistingWorkPolicy) {
        val allowMobile = settings.current().allowMobileNetwork
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (allowMobile) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_TASK_ID, id).build())
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(id), policy, request)
    }

    private fun toTask(entity: DownloadTaskEntity): DownloadTask? {
        val track = decodeTrack(entity) ?: return null
        val status = runCatching { DownloadStatus.valueOf(entity.status) }
            .getOrDefault(DownloadStatus.FAILED)
        return DownloadTask(
            id = entity.id,
            track = track,
            status = status,
            bytesDownloaded = entity.bytesDownloaded,
            totalBytes = entity.totalBytes,
            filePath = entity.filePath,
            error = entity.error,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            qualityKbps = entity.qualityKbps,
            downloadLyrics = entity.downloadLyrics,
            downloadArtwork = entity.downloadArtwork,
        )
    }

    private fun extensionFor(url: String): String {
        val path = runCatching { url.toUri().lastPathSegment }
            .getOrNull()
            ?.lowercase()
            .orEmpty()
        val extension = path.substringAfterLast('.', "")
        return if (extension in AUDIO_EXTENSIONS) extension else "mp3"
    }

    private fun taskId(
        trackId: String,
        providerId: String,
        qualityKbps: Int,
        lyrics: Boolean,
        artwork: Boolean,
    ): String = "$trackId@$providerId@$qualityKbps@${if (lyrics) 1 else 0}${if (artwork) 1 else 0}"

    private fun workName(id: String): String = "download-$id"

    companion object {
        const val WORK_TAG = "music-download"
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }
}
