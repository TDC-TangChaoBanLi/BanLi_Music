/**
 * 文件作用：执行单首歌曲下载，处理缓存复用、断点续传与文件发布。
 */
package com.banlimusic.app.domain.download

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.banlimusic.app.AppContainer
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.network.JsonHttpClient
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
            val settings = container.settingsRepository.current()
            val quality = QualityTiers.normalize(
                entity.qualityKbps.takeIf { it > 0 } ?: settings.qualityKbps,
            )
            val cached = container.cacheRepository.findForTrack(track.id, quality)
            val fullyCached = cached != null &&
                container.playbackCache.isComplete(cached.cacheKey, cached.totalBytes)
            // A complete cache can be copied without spending an API request.
            val stream = if (fullyCached) null else {
                container.musicRepository.resolveStream(track, quality)
            }
            val downloadUrl = stream?.url
                ?: "https://banlimusic.invalid/cache/${cached?.cacheKey}.${cached?.fileExtension ?: "mp3"}"
            val target = repository.downloadTarget()
            val fileName = repository.suggestedFileName(track, downloadUrl, entity.id)
            val tempFile = tempFile(entity.id)
            repository.updateStatus(taskId, DownloadStatus.DOWNLOADING)

            var lastDbUpdate = 0L
            var lastNotificationUpdate = 0L
            var latestTotal = 0L
            val onProgress: suspend (Long, Long) -> Unit = { bytes, total ->
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
                        setForeground(
                            foregroundInfo(
                                track.title,
                                percent ?: 0,
                                indeterminate = percent == null,
                            ),
                        )
                    }
                }
            }

            // Playback already cached part or all of the stream. Converting the
            // cache avoids downloading the same bytes again.
            val cachedBytes = cached?.let {
                runCatching { container.playbackCache.cachedBytes(it.cacheKey) }.getOrDefault(0L)
            } ?: 0L
            if (cachedBytes > 0L && cached != null) {
                val expectedTotal = cached.totalBytes
                    .takeIf { it > 0L }
                    ?: stream?.sizeBytes?.takeIf { it > 0L }
                    ?: 0L
                latestTotal = expectedTotal
                copyCachedToFile(
                    container = container,
                    url = downloadUrl,
                    cacheKey = cached.cacheKey,
                    file = tempFile,
                    expectedTotal = expectedTotal,
                    onProgress = onProgress,
                )
            } else {
                downloadToFile(
                    client = container.downloadHttpClient,
                    url = downloadUrl,
                    file = tempFile,
                    onProgress = onProgress,
                )
            }

            repository.updateStatus(taskId, DownloadStatus.VERIFYING)
            val length = tempFile.length()
            if (length <= 0L) throw IOException("下载的文件为空")
            if (latestTotal > 0L && length < latestTotal - 1024L) {
                throw IOException("文件不完整，请重试")
            }
            val location = publish(tempFile, target, fileName, track)
            tempFile.delete()
            repository.updateProgress(taskId, length, if (latestTotal > 0) latestTotal else length)
            writeSidecars(container, track, target, fileName, entity)
            container.cacheRepository.removeTrack(track.id)
            container.cacheRepository.trimTo(settings.cacheMaxBytes)
            repository.updateStatus(taskId, DownloadStatus.COMPLETED, filePath = location)
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

    private fun tempFile(taskId: String): File {
        val directory = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        return File(directory, "${TextNormalizer.sanitizeFileName(taskId)}.part")
    }

    private suspend fun publish(
        tempFile: File,
        target: DownloadTarget,
        fileName: String,
        track: Track,
    ): String = withContext(Dispatchers.IO) {
        when (target) {
            DownloadTarget.MediaStoreMusic -> publishToMediaStore(tempFile, fileName, track)
            is DownloadTarget.Directory -> {
                val destination = File(target.directory, fileName)
                tempFile.copyTo(destination, overwrite = true)
                destination.absolutePath
            }
        }
    }

    private fun publishToMediaStore(
        tempFile: File,
        fileName: String,
        track: Track,
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("当前系统不支持 MediaStore 音乐目录")
        }
        val resolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeFor(fileName))
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/BanLiMusic",
            )
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artistLine)
            track.album?.title?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            track.durationMs?.takeIf { it > 0L }?.let {
                put(MediaStore.Audio.Media.DURATION, it)
            }
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建媒体库条目")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法写入媒体库")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return uri.toString()
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun mimeTypeFor(fileName: String): String = when (
        fileName.substringAfterLast('.', "").lowercase()
    ) {
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "audio/mpeg"
    }

    private suspend fun writeSidecars(
        container: AppContainer,
        track: Track,
        target: DownloadTarget,
        fileName: String,
        entity: DownloadTaskEntity,
    ) {
        val directory = when (target) {
            is DownloadTarget.Directory -> target.directory
            DownloadTarget.MediaStoreMusic -> File(
                applicationContext.filesDir,
                "BanLiMusic/Sidecars",
            ).apply { mkdirs() }
        }
        val baseName = fileName.substringBeforeLast('.')
        if (entity.downloadLyrics) {
            try {
                val lyrics = container.musicRepository.lyrics(track)
                if (lyrics != null && !lyrics.isEmpty) {
                    File(directory, "$baseName.lrc").writeText(lyrics.raw)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Sidecar files are optional; the audio download still succeeds.
            }
        }
        if (entity.downloadArtwork) {
            try {
                val artworkUrl = container.musicRepository.artwork(track)
                if (!artworkUrl.isNullOrBlank()) {
                    val target = File(directory, "$baseName.jpg")
                    if (artworkUrl.startsWith("file://")) {
                        val cached = Uri.parse(artworkUrl).path?.let(::File)
                        if (cached != null && cached.exists()) {
                            cached.copyTo(target, overwrite = true)
                        }
                    } else {
                        val request = Request.Builder()
                            .url(artworkUrl)
                            .header("User-Agent", JsonHttpClient.USER_AGENT)
                            .build()
                        container.downloadHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body.byteStream().use { input ->
                                    target.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Sidecar files are optional.
            }
        }
    }

    /**
     * Copies the stream through the playback cache. Fully cached tracks never hit
     * the network; partially cached ones only fetch the missing ranges.
     */
    private suspend fun copyCachedToFile(
        container: AppContainer,
        url: String,
        cacheKey: String?,
        file: File,
        expectedTotal: Long,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val playbackCache = container.playbackCache
        val dataSource = playbackCache.readSource()
        var written = 0L
        try {
            dataSource.open(playbackCache.dataSpec(url, cacheKey))
            FileOutputStream(file, false).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (isStopped) throw CancellationException("download stopped")
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                    onProgress(written, expectedTotal.takeIf { it > 0L } ?: written)
                }
                output.flush()
            }
        } finally {
            runCatching { dataSource.close() }
        }
        written
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
