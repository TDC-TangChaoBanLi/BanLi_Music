package com.banlimusic.desktop.data

import com.banlimusic.app.core.cache.CacheUsage
import com.banlimusic.app.core.cache.CachedTrack
import com.banlimusic.app.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Playback cache for the desktop player. Audio is written while it streams and
 * only promoted to a complete entry after the decoder reaches the end, so a
 * cancelled playback never leaves a truncated file that looks usable.
 */
class DesktopPlaybackCache(
    root: File,
    private val indexFile: File,
    private val json: Json,
    private val maxBytesProvider: () -> Long,
) {

    private val directory = File(root, "audio").apply { mkdirs() }
    private val _entries = MutableStateFlow<List<CachedTrack>>(emptyList())
    val entries: StateFlow<List<CachedTrack>> = _entries.asStateFlow()

    init {
        directory.listFiles()
            ?.filter { it.name.endsWith(PART_SUFFIX) }
            ?.forEach { runCatching { it.delete() } }
        load()
    }

    /** Returns a completed cache file, updating its LRU timestamp. */
    suspend fun cachedFile(trackId: String, qualityKbps: Int): File? = withContext(Dispatchers.IO) {
        val key = cacheKey(trackId, qualityKbps)
        val entry = _entries.value.firstOrNull { it.cacheKey == key } ?: return@withContext null
        val file = File(directory, entry.fileExtension.let { "${keyFile(key)}.$it" })
        if (!file.isFile || file.length() <= 0L) {
            remove(key)
            return@withContext null
        }
        val now = System.currentTimeMillis()
        _entries.value = _entries.value.map {
            if (it.cacheKey == key) it.copy(cachedBytes = file.length(), lastAccessAt = now) else it
        }
        persist()
        file
    }

    fun begin(
        track: Track,
        qualityKbps: Int,
        extension: String,
        expectedBytes: Long,
    ): CacheSink {
        val key = cacheKey(track.id, qualityKbps)
        val safeExtension = extension.lowercase().ifBlank { "mp3" }
        val finalFile = File(directory, "${keyFile(key)}.$safeExtension")
        val tempFile = File(directory, "${keyFile(key)}.$safeExtension$PART_SUFFIX")
        runCatching { tempFile.delete() }
        return CacheSink(
            cacheKey = key,
            track = track,
            qualityKbps = qualityKbps,
            extension = safeExtension,
            expectedBytes = expectedBytes.takeIf { it > 0L },
            tempFile = tempFile,
            finalFile = finalFile,
            output = tempFile.outputStream(),
            createdAt = System.currentTimeMillis(),
        )
    }

    suspend fun commit(sink: CacheSink) = withContext(Dispatchers.IO) {
        runCatching {
            sink.output.flush()
            sink.output.close()
            if (!sink.tempFile.isFile || sink.tempFile.length() <= 0L) {
                sink.tempFile.delete()
                return@withContext
            }
            Files.move(
                sink.tempFile.toPath(),
                sink.finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            val length = sink.finalFile.length()
            val now = System.currentTimeMillis()
            val entry = CachedTrack(
                cacheKey = sink.cacheKey,
                track = sink.track,
                qualityKbps = sink.qualityKbps,
                cachedBytes = length,
                totalBytes = length,
                fileExtension = sink.extension,
                createdAt = sink.createdAt,
                lastAccessAt = now,
            )
            _entries.value = (listOf(entry) + _entries.value.filterNot {
                it.cacheKey == sink.cacheKey
            }).sortedByDescending { it.lastAccessAt }
            persist()
            trim()
        }
    }

    fun abort(sink: CacheSink) {
        runCatching { sink.output.close() }
        runCatching { sink.tempFile.delete() }
    }

    fun fileFor(cacheKey: String): File? {
        val entry = _entries.value.firstOrNull { it.cacheKey == cacheKey } ?: return null
        val file = File(directory, "${keyFile(cacheKey)}.${entry.fileExtension}")
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun usage(): CacheUsage = withContext(Dispatchers.IO) {
        val bytes = directorySize()
        CacheUsage(totalBytes = bytes, audioBytes = bytes)
    }

    suspend fun remove(cacheKey: String) = withContext(Dispatchers.IO) {
        val entry = _entries.value.firstOrNull { it.cacheKey == cacheKey }
        entry?.let { File(directory, "${keyFile(cacheKey)}.${it.fileExtension}").delete() }
        _entries.value = _entries.value.filterNot { it.cacheKey == cacheKey }
        persist()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        _entries.value = emptyList()
        persist()
    }

    suspend fun trim() = withContext(Dispatchers.IO) {
        val maxBytes = maxBytesProvider().coerceAtLeast(0L)
        var total = directorySize()
        if (total <= maxBytes) return@withContext
        _entries.value
            .sortedBy { it.lastAccessAt }
            .forEach { entry ->
                if (total <= maxBytes) return@forEach
                val file = File(directory, "${keyFile(entry.cacheKey)}.${entry.fileExtension}")
                val length = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    total -= length
                }
                _entries.value = _entries.value.filterNot { it.cacheKey == entry.cacheKey }
            }
        persist()
    }

    private fun load() {
        val records = runCatching {
            json.decodeFromString(
                DesktopPlaybackCacheIndex.serializer(),
                indexFile.readText(),
            ).entries
        }.getOrDefault(emptyList())
        _entries.value = records.mapNotNull { record ->
            val file = File(directory, "${keyFile(record.cacheKey)}.${record.fileExtension}")
            if (!file.isFile || file.length() <= 0L) return@mapNotNull null
            val length = file.length()
            CachedTrack(
                cacheKey = record.cacheKey,
                track = record.track,
                qualityKbps = record.qualityKbps,
                cachedBytes = length,
                totalBytes = record.totalBytes.takeIf { it >= length } ?: length,
                fileExtension = record.fileExtension,
                createdAt = record.createdAt,
                lastAccessAt = record.lastAccessAt,
            )
        }
    }

    private fun persist() {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val document = DesktopPlaybackCacheIndex(
                entries = _entries.value.map { entry ->
                    DesktopPlaybackCacheRecord(
                        cacheKey = entry.cacheKey,
                        track = entry.track,
                        qualityKbps = entry.qualityKbps,
                        totalBytes = entry.totalBytes,
                        fileExtension = entry.fileExtension,
                        createdAt = entry.createdAt,
                        lastAccessAt = entry.lastAccessAt,
                    )
                },
            )
            val temp = File(indexFile.parentFile, "${indexFile.name}.tmp")
            temp.writeText(json.encodeToString(DesktopPlaybackCacheIndex.serializer(), document))
            runCatching {
                Files.move(
                    temp.toPath(),
                    indexFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                Files.move(temp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun directorySize(): Long = directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

    private fun keyFile(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun cacheKey(trackId: String, qualityKbps: Int): String = "$trackId:$qualityKbps"

    data class CacheSink(
        val cacheKey: String,
        val track: Track,
        val qualityKbps: Int,
        val extension: String,
        val expectedBytes: Long?,
        val tempFile: File,
        val finalFile: File,
        val output: OutputStream,
        val createdAt: Long,
    ) {
        @Volatile
        var failed: Boolean = false
    }

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}

@Serializable
private data class DesktopPlaybackCacheIndex(
    val version: Int = 1,
    val entries: List<DesktopPlaybackCacheRecord> = emptyList(),
)

@Serializable
private data class DesktopPlaybackCacheRecord(
    val cacheKey: String,
    val track: Track,
    val qualityKbps: Int = 0,
    val totalBytes: Long = 0L,
    val fileExtension: String = "mp3",
    val createdAt: Long = 0L,
    val lastAccessAt: Long = 0L,
)
