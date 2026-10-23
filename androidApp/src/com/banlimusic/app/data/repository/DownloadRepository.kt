package com.banlimusic.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.net.toUri
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.database.dao.DownloadDao
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.domain.download.DownloadStatus
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
    private val json: Json,
    private val settings: SettingsRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {

    fun observeTasks(): Flow<List<DownloadTask>> = dao.observeAll().map { entities ->
        entities.mapNotNull(::toTask)
    }

    suspend fun completedBytes(): Long = dao.completedBytes()

    suspend fun enqueue(track: Track): Result<Unit> = runCatching {
        val source = track.sources.firstOrNull { TrackAction.DOWNLOAD in it.availableActions }
            ?: track.sources.firstOrNull { TrackAction.STREAM in it.availableActions }
            ?: throw IllegalStateException("没有可下载的来源")
        val taskId = taskId(track.id, source.providerId)
        val existing = dao.getById(taskId)
        if (existing != null && existing.status == DownloadStatus.COMPLETED.name) {
            val path = existing.filePath
            if (path != null && File(path).exists()) return@runCatching
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
            ),
        )
        schedule(taskId, ExistingWorkPolicy.KEEP)
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
        entity.filePath?.let { runCatching { File(it).delete() } }
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
        entity.filePath?.let { runCatching { File(it).delete() } }
        dao.delete(id)
    }

    suspend fun clearCompleted() {
        val completed = dao.getCompleted()
        completed.forEach { entity ->
            entity.filePath?.let { runCatching { File(it).delete() } }
        }
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

    private fun taskId(trackId: String, providerId: String): String = "$trackId@$providerId"

    private fun workName(id: String): String = "download-$id"

    companion object {
        const val WORK_TAG = "music-download"
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }
}
