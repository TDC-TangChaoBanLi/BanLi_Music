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
import com.banlimusic.app.core.model.DownloadQueueOutcome
import com.banlimusic.app.core.model.DownloadQualityConflict
import com.banlimusic.app.core.model.DownloadQualityDecision
import com.banlimusic.app.core.model.DownloadQualityPolicy
import com.banlimusic.app.core.model.DownloadRequestItem
import com.banlimusic.app.core.model.QualityTiers
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
    private val manifest: DownloadManifestRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {

    fun observeTasks(): Flow<List<DownloadTask>> = dao.observeAll().map { entities ->
        entities.mapNotNull(::toTask)
    }

    suspend fun completedBytes(): Long = dao.completedBytes()

    suspend fun hasCompleted(trackId: String): Boolean =
        dao.getCompletedForTrack(trackId).any { locationExists(it.filePath) }

    /** Resolves the quality used by UI dialog copy and enqueue calls. */
    suspend fun resolveRequestedQuality(qualityKbps: Int? = null): Int {
        val current = settings.current()
        val requested = qualityKbps
            ?: current.downloadQualityKbps.takeIf { it > 0 }
            ?: current.qualityKbps
        return QualityTiers.normalize(requested)
    }

    /** Completed versions whose quality differs from the requested quality. */
    suspend fun findConflicts(
        items: Collection<DownloadRequestItem>,
    ): List<DownloadQualityConflict> = items
        .distinctBy { it.track.id }
        .mapNotNull { item ->
            val quality = QualityTiers.normalize(item.qualityKbps)
            val qualities = qualitiesOf(completedEntities(item.track.id))
            if (qualities.isNotEmpty() && quality !in qualities) {
                DownloadQualityConflict(
                    track = item.track,
                    existingQualities = qualities,
                    requestedQualityKbps = quality,
                )
            } else {
                null
            }
        }

    suspend fun enqueue(
        track: Track,
        qualityKbps: Int? = null,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
    ): Result<Unit> = enqueueInternal(
        track = track,
        qualityKbps = qualityKbps,
        downloadLyrics = downloadLyrics,
        downloadArtwork = downloadArtwork,
    )

    private suspend fun enqueueInternal(
        track: Track,
        qualityKbps: Int? = null,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
    ): Result<Unit> = runCatching {
        val current = settings.current()
        val source = track.sources.firstOrNull { TrackAction.DOWNLOAD in it.availableActions }
            ?: track.sources.firstOrNull { TrackAction.STREAM in it.availableActions }
            ?: throw IllegalStateException("没有可下载的来源")
        val quality = QualityTiers.normalize(
            qualityKbps
                ?: current.downloadQualityKbps.takeIf { it > 0 }
                ?: current.qualityKbps,
        )
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

        // A finished copy at the same quality already satisfies this request;
        // other qualities are handled by the explicit conflict policy before
        // reaching this point.
        val sameQuality = dao.getCompletedForTrack(track.id)
            .firstOrNull { it.qualityKbps == quality && locationExists(it.filePath) }
        if (sameQuality != null) {
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
     * exceed the configured GD Music request budget. [decision] is only applied
     * to tracks that already have a completed version at another quality.
     */
    suspend fun enqueueAll(
        items: Collection<DownloadRequestItem>,
        decision: DownloadQualityDecision = DownloadQualityDecision.COEXIST,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
    ): DownloadQueueOutcome {
        var queued = 0
        var skipped = 0
        var replaced = 0
        var failed = 0
        items.distinctBy { it.track.id }.forEach { item ->
            try {
                val quality = QualityTiers.normalize(
                    item.qualityKbps.takeIf { it > 0 }
                        ?: resolveRequestedQuality(),
                )
                val existing = completedEntities(item.track.id)
                val plan = DownloadQualityPolicy.plan(
                    decision = decision,
                    existingQualities = qualitiesOf(existing),
                    requestedQualityKbps = quality,
                )
                if (!plan.download) {
                    skipped++
                    return@forEach
                }
                if (plan.deleteExisting && existing.isNotEmpty()) {
                    deleteCompletedVersions(existing)
                    replaced++
                }
                val result = enqueue(
                    track = item.track,
                    qualityKbps = quality,
                    downloadLyrics = downloadLyrics,
                    downloadArtwork = downloadArtwork,
                )
                if (result.isSuccess) queued++ else failed++
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failed++
            }
        }
        return DownloadQueueOutcome(
            queued = queued,
            skipped = skipped,
            replaced = replaced,
            failed = failed,
        )
    }

    /** Convenience overload for callers that already resolved one quality. */
    suspend fun enqueueAll(
        tracks: Collection<Track>,
        qualityKbps: Int? = null,
        downloadLyrics: Boolean? = null,
        downloadArtwork: Boolean? = null,
    ): DownloadQueueOutcome {
        val quality = resolveRequestedQuality(qualityKbps)
        return enqueueAll(
            items = tracks.map { DownloadRequestItem(it, quality) },
            downloadLyrics = downloadLyrics,
            downloadArtwork = downloadArtwork,
        )
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
        removeManifestEntries(listOf(entity))
        dao.delete(id)
    }

    suspend fun clearCompleted() {
        val completed = dao.getCompleted()
        completed.forEach { entity -> deleteLocation(entity.filePath) }
        removeManifestEntries(completed)
        dao.deleteCompleted()
    }

    /** Keeps the portable manifest in sync when downloads disappear. */
    private suspend fun removeManifestEntries(entities: List<DownloadTaskEntity>) {
        if (entities.isEmpty()) return
        val target = downloadTarget()
        entities.forEach { entity ->
            manifest.remove(
                target = target,
                trackId = entity.trackId.ifBlank { entity.id.substringBeforeLast('@') },
                qualityKbps = entity.qualityKbps.takeIf { it > 0 },
            )
        }
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
        // Coexisting qualities are allowed, but playback always prefers the
        // highest downloaded quality regardless of the playback setting.
        return dao.getCompleted()
            .asSequence()
            .filter { entity -> locationExists(entity.filePath) }
            .mapNotNull { entity ->
                val path = entity.filePath ?: return@mapNotNull null
                val id = entity.trackId.ifBlank { entity.id.substringBeforeLast('@') }
                if (id !in wanted) return@mapNotNull null
                id to entity
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, versions) ->
                versions.maxByOrNull { it.qualityKbps }!!.filePath!!.toUri()
            }
    }

    private suspend fun completedEntities(trackId: String): List<DownloadTaskEntity> =
        dao.getCompletedForTrack(trackId).filter { locationExists(it.filePath) }

    private fun qualitiesOf(entities: Collection<DownloadTaskEntity>): List<Int> =
        entities.mapNotNull { it.qualityKbps.takeIf { quality -> quality > 0 } }
            .map(QualityTiers::normalize)
            .distinct()
            .sorted()

    /**
     * Deletes the selected versions and their manifest entries. A legacy row may
     * share its file with another quality, so the file itself is only removed
     * when no surviving row points at it.
     */
    private suspend fun deleteCompletedVersions(entities: List<DownloadTaskEntity>) {
        if (entities.isEmpty()) return
        val ids = entities.map { it.id }.toSet()
        val protectedPaths = dao.getCompleted()
            .asSequence()
            .filter { it.id !in ids }
            .mapNotNull { it.filePath }
            .toSet()
        entities.forEach { entity ->
            if (entity.filePath !in protectedPaths) deleteLocation(entity.filePath)
            deleteTemp(entity.id)
        }
        removeManifestEntries(entities)
        entities.forEach { entity -> dao.delete(entity.id) }
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
