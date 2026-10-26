/**
 * 文件作用：在下载目录读写歌曲元数据清单，并把外部清单导入为已下载内容。
 */
package com.banlimusic.app.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.banlimusic.app.core.cache.MediaMetadataCache
import com.banlimusic.app.core.database.dao.DownloadDao
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import com.banlimusic.app.core.model.DownloadManifest
import com.banlimusic.app.core.model.DownloadManifestEntry
import com.banlimusic.app.core.model.DownloadManifestOps
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.download.DownloadStatus
import com.banlimusic.app.domain.download.DownloadTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

data class ManifestImportResult(
    val total: Int = 0,
    val imported: Int = 0,
    val missing: Int = 0,
    val failed: Boolean = false,
)

/**
 * Owns `banli-downloads.json`. The manifest is written next to the audio files
 * so copying the folder to another device keeps track metadata, provider
 * sources and lyric text. Reads are best-effort: Android 11+ cannot read files
 * owned by another app, so the manual import path is the portable fallback.
 */
class DownloadManifestRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val json: Json,
    private val metadataCache: MediaMetadataCache,
) {
    private val mutex = Mutex()

    suspend fun record(target: DownloadTarget, entry: DownloadManifestEntry) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = read(target)
            write(target, DownloadManifestOps.upsert(current, entry))
        }
    }

    suspend fun remove(
        target: DownloadTarget,
        trackId: String,
        qualityKbps: Int? = null,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = read(target)
            write(target, DownloadManifestOps.remove(current, trackId, qualityKbps))
        }
    }

    /** Imports a manifest picked through SAF and registers usable entries. */
    suspend fun importManifest(uri: Uri): ManifestImportResult = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext ManifestImportResult(failed = true)
        val manifest = decode(raw) ?: return@withContext ManifestImportResult(failed = true)
        register(manifest.entries)
    }

    /**
     * Reads the manifest of an app-readable download directory and registers
     * entries that are missing from the database (e.g. after copying the folder
     * back, or after reinstalling).
     */
    suspend fun syncFromTarget(target: DownloadTarget): Int = withContext(Dispatchers.IO) {
        val entries = read(target).entries
        if (entries.isEmpty()) return@withContext 0
        val directory = (target as? DownloadTarget.Directory)?.directory
        val result = register(entries, directory)
        result.imported
    }

    private suspend fun register(
        entries: List<DownloadManifestEntry>,
        directory: File? = null,
    ): ManifestImportResult {
        var imported = 0
        var missing = 0
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            if (entry.trackId.isBlank() || entry.fileName.isBlank()) return@forEach
            val existing = dao.getCompletedForTrack(entry.trackId)
            // MediaStore file paths are content URIs without the file name, so
            // also treat an existing download of the same quality as registered.
            if (existing.any {
                    it.filePath?.contains(entry.fileName) == true ||
                        it.qualityKbps == entry.qualityKbps
                }
            ) {
                return@forEach
            }
            val location = resolveAudioUri(entry.fileName, directory)
            if (location == null) {
                missing++
                return@forEach
            }
            val entity = DownloadTaskEntity(
                id = importTaskId(entry),
                trackJson = json.encodeToString(Track.serializer(), entry.track.copy(
                    id = entry.track.id.ifBlank { entry.trackId },
                )),
                title = entry.track.title,
                artist = entry.track.artistLine,
                providerId = entry.track.sources.firstOrNull()?.providerId.orEmpty(),
                providerTrackId = entry.track.sources.firstOrNull()?.providerTrackId.orEmpty(),
                status = DownloadStatus.COMPLETED.name,
                bytesDownloaded = entry.sizeBytes,
                totalBytes = entry.sizeBytes,
                filePath = location,
                error = null,
                retryCount = 0,
                createdAt = entry.downloadedAt.takeIf { it > 0L } ?: now,
                updatedAt = now,
                trackId = entry.trackId,
                qualityKbps = entry.qualityKbps,
                downloadLyrics = !entry.lyricsText.isNullOrBlank(),
                downloadArtwork = !entry.artworkFileName.isNullOrBlank(),
            )
            dao.upsert(entity)
            entry.lyricsText
                ?.takeIf { it.isNotBlank() }
                ?.let { text ->
                    runCatching {
                        metadataCache.putLyrics(entry.trackId, Lyrics(raw = text))
                    }
                }
            imported++
        }
        return ManifestImportResult(
            total = entries.size,
            imported = imported,
            missing = missing,
        )
    }

    private fun resolveAudioUri(fileName: String, directory: File? = null): String? {
        if (fileName.isBlank()) return null
        if (directory != null) {
            val localFile = File(directory, fileName)
            if (localFile.exists() && localFile.length() > 0L) {
                return Uri.fromFile(localFile).toString()
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                ContentUris.withAppendedId(collection, id).toString()
            }
        }.getOrNull()
    }

    private fun importTaskId(entry: DownloadManifestEntry): String =
        "import:${entry.trackId}@${entry.qualityKbps}"

    // --- storage ---------------------------------------------------------

    private fun read(target: DownloadTarget): DownloadManifest = when (target) {
        is DownloadTarget.Directory -> readFile(target.directory)
        DownloadTarget.MediaStoreMusic -> readMediaStore()
    }

    private fun write(target: DownloadTarget, manifest: DownloadManifest) {
        val text = json.encodeToString(DownloadManifest.serializer(), manifest)
        when (target) {
            is DownloadTarget.Directory -> writeFile(target.directory, text)
            DownloadTarget.MediaStoreMusic -> writeMediaStore(text)
        }
    }

    private fun readFile(directory: File): DownloadManifest {
        val file = File(directory, DownloadManifestOps.FILE_NAME)
        if (!file.exists()) return DownloadManifest()
        return runCatching { decode(file.readText()) }.getOrNull() ?: DownloadManifest()
    }

    private fun writeFile(directory: File, text: String) {
        runCatching {
            if (!directory.exists() && !directory.mkdirs()) return
            val target = File(directory, DownloadManifestOps.FILE_NAME)
            val temp = File(directory, "${DownloadManifestOps.FILE_NAME}.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        }
    }

    private fun readMediaStore(): DownloadManifest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return DownloadManifest()
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID),
                mediaStoreSelection,
                mediaStoreSelectionArgs,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use DownloadManifest()
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                decode(text.orEmpty())
            }
        }.getOrNull() ?: DownloadManifest()
    }

    private fun writeMediaStore(text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID),
                mediaStoreSelection,
                mediaStoreSelectionArgs,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0))
                } else {
                    null
                }
            }
            if (existing != null) {
                resolver.openOutputStream(existing, "wt")?.use { output ->
                    output.write(text.toByteArray())
                }
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, DownloadManifestOps.FILE_NAME)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, MEDIA_STORE_DIRECTORY)
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return@runCatching
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(text.toByteArray())
                    }
                    resolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                        },
                        null,
                        null,
                    )
                } catch (error: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw error
                }
            }
        }
    }

    private fun decode(raw: String): DownloadManifest? {
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString(DownloadManifest.serializer(), raw)
        }.getOrNull()
    }

    private companion object {
        val MEDIA_STORE_DIRECTORY = "${Environment.DIRECTORY_MUSIC}/BanLiMusic"
        val mediaStoreSelection =
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
        val mediaStoreSelectionArgs = arrayOf(
            DownloadManifestOps.FILE_NAME,
            "$MEDIA_STORE_DIRECTORY/",
        )
    }
}
