/**
 * 文件作用：持久化桌面下载任务，并执行暂停、重试、缓存转存与音质冲突策略。
 */
package com.banlimusic.desktop.data

import com.banlimusic.app.core.common.MusicException
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.model.DownloadQueueOutcome
import com.banlimusic.app.core.model.DownloadQualityConflict
import com.banlimusic.app.core.model.DownloadQualityDecision
import com.banlimusic.app.core.model.DownloadQualityPolicy
import com.banlimusic.app.core.model.DownloadRequestItem
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.desktop.DesktopPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

enum class DesktopDownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DesktopDownloadTask(
    val id: String,
    val track: Track,
    val qualityKbps: Int,
    val status: DesktopDownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val filePath: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = when {
            status == DesktopDownloadStatus.COMPLETED -> 1f
            totalBytes <= 0L -> 0f
            else -> (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }

    val isActive: Boolean
        get() = status == DesktopDownloadStatus.QUEUED ||
            status == DesktopDownloadStatus.DOWNLOADING

    val canPause: Boolean get() = isActive

    val canResume: Boolean
        get() = status == DesktopDownloadStatus.PAUSED ||
            status == DesktopDownloadStatus.FAILED ||
            status == DesktopDownloadStatus.CANCELLED

    val file: File? get() = filePath?.let(::File)
}

/** Sequential-friendly download manager with JSON persistence and Range-free retry. */
class DesktopDownloadManager(
    private val file: Path,
    private val json: Json,
    private val musicRepository: DesktopMusicRepository,
    private val settings: DesktopSettingsRepository,
    private val scope: CoroutineScope,
    private val httpClient: OkHttpClient,
) {
    private val _tasks = MutableStateFlow<List<DesktopDownloadTask>>(emptyList())
    val tasks: StateFlow<List<DesktopDownloadTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    init {
        load()
    }

    /** Completed versions whose quality differs from the requested quality. */
    suspend fun findConflicts(
        items: Collection<DownloadRequestItem>,
    ): List<DownloadQualityConflict> = withContext(Dispatchers.Default) {
        items.distinctBy { it.track.id }.mapNotNull { item ->
            val quality = normalizeQuality(item.qualityKbps)
            val qualities = completedVersions(item.track.id).map { it.qualityKbps }.distinct().sorted()
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
    }

    /** Applies one batch decision and returns the same outcome shape as Android. */
    suspend fun enqueue(
        items: Collection<DownloadRequestItem>,
        decision: DownloadQualityDecision = DownloadQualityDecision.COEXIST,
    ): DownloadQueueOutcome = withContext(Dispatchers.Default) {
        var queued = 0
        var skipped = 0
        var replaced = 0
        var failed = 0
        items.distinctBy { it.track.id }.forEach { item ->
            try {
                val quality = normalizeQuality(item.qualityKbps)
                val existing = completedVersions(item.track.id)
                val plan = DownloadQualityPolicy.plan(
                    decision = decision,
                    existingQualities = existing.map { it.qualityKbps },
                    requestedQualityKbps = quality,
                )
                if (!plan.download) {
                    skipped++
                    return@forEach
                }
                if (plan.deleteExisting && existing.isNotEmpty()) {
                    existing.forEach { remove(it.id, deleteFile = true) }
                    replaced++
                }
                val created = enqueueNow(DownloadRequestItem(item.track, quality))
                if (created) queued++ else skipped++
            } catch (_: Throwable) {
                failed++
            }
        }
        DownloadQueueOutcome(
            queued = queued,
            skipped = skipped,
            replaced = replaced,
            failed = failed,
        )
    }

    private fun enqueueNow(item: DownloadRequestItem): Boolean {
        val quality = normalizeQuality(item.qualityKbps)
        val id = taskId(item.track.id, quality)
        if (_tasks.value.any { it.id == id && (it.isActive || it.status == DesktopDownloadStatus.COMPLETED) }) {
            return false
        }
        val now = System.currentTimeMillis()
        val task = DesktopDownloadTask(
            id = id,
            track = item.track,
            qualityKbps = quality,
            status = DesktopDownloadStatus.QUEUED,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            filePath = null,
            error = null,
            createdAt = now,
            updatedAt = now,
        )
        _tasks.update { current ->
            listOf(task) + current.filterNot { it.id == id }
        }
        persist()
        start(task)
        return true
    }

    fun completedFile(trackId: String): File? = _tasks.value
        .asSequence()
        .filter {
            it.track.id == trackId &&
                it.status == DesktopDownloadStatus.COMPLETED &&
                it.file?.isFile == true
        }
        .maxByOrNull { it.qualityKbps }
        ?.file

    private fun completedVersions(trackId: String): List<DesktopDownloadTask> =
        _tasks.value.filter {
            it.track.id == trackId &&
                it.status == DesktopDownloadStatus.COMPLETED &&
                it.file?.isFile == true
        }

    private fun normalizeQuality(qualityKbps: Int): Int =
        QualityTiers.normalize(qualityKbps).coerceIn(128, 320)

    private fun taskId(trackId: String, qualityKbps: Int): String =
        "$trackId@$qualityKbps"

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        updateTask(id) {
            it.copy(
                status = DesktopDownloadStatus.CANCELLED,
                error = "已取消",
                updatedAt = System.currentTimeMillis(),
            )
        }
        persist()
    }

    fun pause(id: String) {
        jobs.remove(id)?.cancel()
        updateTask(id) {
            it.copy(
                status = DesktopDownloadStatus.PAUSED,
                error = "已暂停",
                updatedAt = System.currentTimeMillis(),
            )
        }
        persist()
    }

    fun retry(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.isActive) return
        updateTask(id) {
            it.copy(
                status = DesktopDownloadStatus.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                error = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
        persist()
        start(_tasks.value.first { it.id == id })
    }

    fun remove(id: String, deleteFile: Boolean = true) {
        jobs.remove(id)?.cancel()
        val task = _tasks.value.firstOrNull { it.id == id }
        if (deleteFile && task != null) deleteTaskFiles(task)
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearCompleted() {
        val completed = _tasks.value.filter { it.status == DesktopDownloadStatus.COMPLETED }
        completed.forEach { deleteTaskFiles(it) }
        _tasks.update { list -> list.filterNot { it.status == DesktopDownloadStatus.COMPLETED } }
        persist()
    }

    /** Copies a completed playback-cache file into the download directory. */
    suspend fun adoptCached(
        track: Track,
        source: File,
        qualityKbps: Int,
        decision: DownloadQualityDecision = DownloadQualityDecision.COEXIST,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() <= 0L) return@withContext false
        val quality = normalizeQuality(qualityKbps)
        val existing = completedVersions(track.id)
        val plan = DownloadQualityPolicy.plan(
            decision = decision,
            existingQualities = existing.map { it.qualityKbps },
            requestedQualityKbps = quality,
        )
        if (!plan.download) return@withContext false
        if (plan.deleteExisting && existing.isNotEmpty()) {
            existing.forEach { remove(it.id, deleteFile = true) }
        }
        val id = taskId(track.id, quality)
        val directory = downloadDirectory()
        Files.createDirectories(directory)
        val extension = source.extension.lowercase().takeIf { it in AUDIO_EXTENSIONS }
            ?: "mp3"
        val finalFile = directory.resolve("${fileName(track, id)}.$extension").toFile()
        runCatching { source.copyTo(finalFile, overwrite = true) }.getOrElse {
            return@withContext false
        }
        writeSidecars(track, finalFile)
        val now = System.currentTimeMillis()
        val task = DesktopDownloadTask(
            id = id,
            track = track,
            qualityKbps = quality,
            status = DesktopDownloadStatus.COMPLETED,
            bytesDownloaded = finalFile.length(),
            totalBytes = finalFile.length(),
            filePath = finalFile.absolutePath,
            error = null,
            createdAt = now,
            updatedAt = now,
        )
        _tasks.update { list -> listOf(task) + list.filterNot { it.id == id } }
        persist()
        true
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancel)
    }

    private fun start(task: DesktopDownloadTask) {
        jobs[task.id]?.cancel()
        jobs[task.id] = scope.launch {
            runDownload(task)
        }
    }

    private suspend fun runDownload(task: DesktopDownloadTask) {
        val id = task.id
        var target: File? = null
        try {
            updateTask(id) {
                it.copy(
                    status = DesktopDownloadStatus.DOWNLOADING,
                    error = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            persist()

            val info = musicRepository.downloadInfo(task.track, task.qualityKbps)
            val extension = extensionFor(info.url)
            val directory = downloadDirectory()
            Files.createDirectories(directory)
            val baseName = fileName(task.track, id)
            val finalFile = directory.resolve("$baseName.$extension").toFile()
            val partFile = directory.resolve("$baseName.$extension.part").toFile()
            target = finalFile

            val request = Request.Builder()
                .url(info.url)
                .header("User-Agent", JsonHttpClient.USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw MusicException.Server(response.code)
                val total = response.body.contentLength()
                    .takeIf { it > 0L }
                    ?: info.sizeBytes
                    ?: 0L
                updateTask(id) {
                    it.copy(
                        totalBytes = total,
                        bytesDownloaded = 0L,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                partFile.outputStream().use { output ->
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 250L) {
                                lastUpdate = now
                                updateTask(id) {
                                    it.copy(
                                        bytesDownloaded = downloaded,
                                        totalBytes = if (total > 0L) total else downloaded,
                                        updatedAt = now,
                                    )
                                }
                            }
                        }
                        updateTask(id) {
                            it.copy(
                                bytesDownloaded = downloaded,
                                totalBytes = if (total > 0L) total else downloaded,
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                    }
                }
            }

            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            writeSidecars(task.track, finalFile)
            updateTask(id) {
                it.copy(
                    status = DesktopDownloadStatus.COMPLETED,
                    bytesDownloaded = finalFile.length(),
                    totalBytes = finalFile.length(),
                    filePath = finalFile.absolutePath,
                    error = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            persist()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val previous = _tasks.value.firstOrNull { it.id == id }?.status
            val wasCancelled = previous == DesktopDownloadStatus.CANCELLED
            val wasPaused = previous == DesktopDownloadStatus.PAUSED
            updateTask(id) {
                it.copy(
                    status = when {
                        wasPaused -> DesktopDownloadStatus.PAUSED
                        wasCancelled -> DesktopDownloadStatus.CANCELLED
                        else -> DesktopDownloadStatus.FAILED
                    },
                    error = when {
                        wasPaused -> "已暂停"
                        wasCancelled -> "已取消"
                        else -> error.userMessage()
                    },
                    updatedAt = System.currentTimeMillis(),
                )
            }
            persist()
        } finally {
            jobs.remove(id)
            if (_tasks.value.firstOrNull { it.id == id }?.status != DesktopDownloadStatus.COMPLETED) {
                target?.let { runCatching { File(it.parentFile, "${it.name}.part").delete() } }
            }
        }
    }

    private suspend fun writeSidecars(track: Track, audio: File) {
        val current = settings.current()
        val base = File(audio.parentFile, audio.nameWithoutExtension)
        if (current.downloadLyrics) {
            runCatching {
                val lyrics = musicRepository.lyrics(track) ?: return@runCatching
                File(audio.parentFile, "${base.name}.lrc").writeText(lyrics.raw)
            }
        }
        if (current.downloadArtwork) {
            runCatching {
                val artwork = musicRepository.artwork(track) ?: return@runCatching
                val target = File(audio.parentFile, "${base.name}.jpg")
                if (artwork.startsWith("file:")) {
                    val source = File(URI(artwork))
                    if (source.isFile) source.copyTo(target, overwrite = true)
                } else if (artwork.startsWith("http://") || artwork.startsWith("https://")) {
                    val request = Request.Builder()
                        .url(artwork)
                        .header("User-Agent", JsonHttpClient.USER_AGENT)
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body.byteStream().use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadDirectory(): Path {
        val configured = settings.current().downloadDirectory.trim()
        return if (configured.isNotEmpty()) {
            runCatching { Path.of(configured) }.getOrDefault(DesktopPaths.downloadDir)
        } else {
            DesktopPaths.downloadDir
        }
    }

    private fun fileName(track: Track, id: String): String {
        val base = TextNormalizer.sanitizeFileName("${track.title} - ${track.artistLine}")
        val suffix = (id.hashCode() and 0xFFFFFF).toString(16).padStart(6, '0')
        return "$base-$suffix"
    }

    private fun extensionFor(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase()
        val extension = path.substringAfterLast('.', "")
        return if (extension in AUDIO_EXTENSIONS) extension else "mp3"
    }

    private fun deleteTaskFiles(task: DesktopDownloadTask) {
        task.filePath?.let { runCatching { File(it).delete() } }
        val file = task.filePath?.let(::File) ?: return
        val base = file.nameWithoutExtension
        listOf("jpg", "lrc").forEach { extension ->
            runCatching { File(file.parentFile, "$base.$extension").delete() }
        }
    }

    private fun updateTask(id: String, transform: (DesktopDownloadTask) -> DesktopDownloadTask) {
        _tasks.update { list ->
            list.map { task -> if (task.id == id) transform(task) else task }
        }
    }

    private fun load() {
        if (!Files.exists(file)) return
        runCatching {
            val document = json.decodeFromString(
                DesktopDownloadsFile.serializer(),
                Files.readString(file),
            )
            _tasks.value = document.tasks.map { record ->
                val interrupted = record.status == DesktopDownloadStatus.DOWNLOADING ||
                    record.status == DesktopDownloadStatus.QUEUED
                DesktopDownloadTask(
                    id = taskId(record.track.id, record.qualityKbps),
                    track = record.track,
                    qualityKbps = record.qualityKbps,
                    status = if (interrupted) DesktopDownloadStatus.FAILED
                    else record.status,
                    bytesDownloaded = record.bytesDownloaded,
                    totalBytes = record.totalBytes,
                    filePath = record.filePath,
                    error = if (interrupted) "上次下载未完成，请重试" else record.error,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt,
                )
            }.sortedByDescending { it.createdAt }
        }
    }

    private fun persist() {
        runCatching {
            Files.createDirectories(file.parent)
            val document = DesktopDownloadsFile(
                tasks = _tasks.value.map { task ->
                    DesktopDownloadRecord(
                        track = task.track,
                        qualityKbps = task.qualityKbps,
                        status = task.status,
                        bytesDownloaded = task.bytesDownloaded,
                        totalBytes = task.totalBytes,
                        filePath = task.filePath,
                        error = task.error,
                        createdAt = task.createdAt,
                        updatedAt = task.updatedAt,
                    )
                },
            )
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(
                temp,
                json.encodeToString(DesktopDownloadsFile.serializer(), document),
            )
            runCatching {
                Files.move(
                    temp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }
}

@Serializable
private data class DesktopDownloadsFile(
    val version: Int = 1,
    val tasks: List<DesktopDownloadRecord> = emptyList(),
)

@Serializable
private data class DesktopDownloadRecord(
    val track: Track,
    val qualityKbps: Int = 320,
    val status: DesktopDownloadStatus = DesktopDownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val error: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
