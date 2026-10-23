package com.banlimusic.app.domain.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.system.notification.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads one track into the app-specific Music directory. Progress and status
 * live in Room so the UI can be recreated at any time; WorkManager provides retry.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val application = applicationContext as? MusicApplication ?: return Result.failure()
        val container = application.container
        val repository = container.downloadRepository
        val entity = repository.getEntity(taskId) ?: return Result.failure()
        val track = repository.decodeTrack(entity) ?: return Result.failure()

        repository.updateStatus(taskId, DownloadStatus.DOWNLOADING, filePath = entity.filePath)
        val notification = foregroundInfo(track.title, 0, indeterminate = true)
        runCatching { setForeground(notification) }

        return try {
            val quality = container.settingsRepository.current().qualityKbps
            val stream = container.musicRepository.resolveStream(track, quality)
            val file = prepareFile(repository, track, entity.filePath, stream.url, entity.id)
            repository.updateStatus(taskId, DownloadStatus.DOWNLOADING, filePath = file.absolutePath)

            var lastDbUpdate = 0L
            var lastNotificationUpdate = 0L
            var latestTotal = 0L
            downloadToFile(
                client = container.downloadHttpClient,
                url = stream.url,
                file = file,
            ) { bytes, total ->
                latestTotal = total
                val now = System.currentTimeMillis()
                if (now - lastDbUpdate >= 750L) {
                    lastDbUpdate = now
                    repository.updateProgress(taskId, bytes, total)
                }
                if (now - lastNotificationUpdate >= 2_000L) {
                    lastNotificationUpdate = now
                    val percent = if (total > 0) ((bytes * 100) / total).toInt() else null
                    runCatching {
                        setForeground(foregroundInfo(track.title, percent ?: 0, indeterminate = percent == null))
                    }
                }
            }

            repository.updateStatus(taskId, DownloadStatus.VERIFYING, filePath = file.absolutePath)
            val length = file.length()
            if (length <= 0L) throw IOException("下载的文件为空")
            if (latestTotal > 0L && length < latestTotal - 1024L) {
                throw IOException("文件不完整，请重试")
            }
            repository.updateProgress(taskId, length, if (latestTotal > 0) latestTotal else length)
            repository.updateStatus(taskId, DownloadStatus.COMPLETED, filePath = file.absolutePath)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (isStopped) throw CancellationException("download stopped")
            val currentAttempt = runAttemptCount
            repository.updateRetryCount(taskId, currentAttempt + 1)
            val retryable = error is IOException ||
                error is MusicException.Network ||
                error is MusicException.Timeout ||
                (error is MusicException.Server && error.code >= 500)
            if (retryable && currentAttempt < MAX_RETRIES) {
                repository.updateStatus(taskId, DownloadStatus.QUEUED, error = error.userMessage())
                Result.retry()
            } else {
                repository.updateStatus(taskId, DownloadStatus.FAILED, error = error.userMessage())
                Result.failure()
            }
        }
    }

    private fun prepareFile(
        repository: DownloadRepository,
        track: Track,
        existingPath: String?,
        url: String,
        taskId: String,
    ): File {
        existingPath?.let { path ->
            val existing = File(path)
            if (existing.parentFile?.exists() == true) return existing
        }
        val musicRoot = applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            ?: applicationContext.filesDir
        val directory = File(musicRoot, "BanLiMusic").apply { mkdirs() }
        return File(directory, repository.suggestedFileName(track, url, taskId))
    }

    private suspend fun downloadToFile(
        client: OkHttpClient,
        url: String,
        file: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        var restarted = false
        repeat(2) {
            val existing = if (file.exists()) file.length() else 0L
            val request = Request.Builder()
                .url(url)
                .apply { if (!restarted && existing > 0L) header("Range", "bytes=$existing-") }
                .build()
            var totalForAttempt = 0L

            val outcome = client.newCall(request).execute().use { response ->
                if (response.code == 416 && existing > 0L) {
                    if (file.exists()) file.delete()
                    return@use AttemptOutcome.Restart
                }
                if (!response.isSuccessful) {
                    throw when (response.code) {
                        401, 403 -> MusicException.Unauthorized()
                        404, 410 -> MusicException.NotFound()
                        429 -> MusicException.RateLimited(5 * 60_000L)
                        else -> MusicException.Server(response.code)
                    }
                }

                val append = response.code == 206 && existing > 0L
                if (!append && file.exists()) file.delete()
                val body = response.body
                val contentLength = body.contentLength()
                totalForAttempt = if (append) existing + contentLength else contentLength
                if (totalForAttempt < 0) totalForAttempt = 0L
                var written = if (append) existing else 0L

                body.byteStream().use { input ->
                    FileOutputStream(file, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped) throw CancellationException("download stopped")
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, totalForAttempt)
                        }
                        output.flush()
                    }
                }
                AttemptOutcome.Done(totalForAttempt)
            }

            when (outcome) {
                is AttemptOutcome.Done -> return@withContext outcome.bytes
                AttemptOutcome.Restart -> restarted = true
            }
        }
        throw IOException("无法下载该文件")
    }

    private fun foregroundInfo(title: String, percent: Int, indeterminate: Boolean): ForegroundInfo {
        val notification = Notifications.downloadNotification(
            context = applicationContext,
            title = "正在下载",
            detail = title,
            progressPercent = percent,
            indeterminate = indeterminate,
        )
        val notificationId = Notifications.downloadNotificationId(id.toString())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private sealed interface AttemptOutcome {
        data class Done(val bytes: Long) : AttemptOutcome
        data object Restart : AttemptOutcome
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val MAX_RETRIES = 2
    }
}
