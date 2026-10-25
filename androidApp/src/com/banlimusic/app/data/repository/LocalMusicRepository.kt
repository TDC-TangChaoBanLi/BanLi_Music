/**
 * 文件作用：通过 MediaStore 与文件 URI 导入本地音乐并维护索引。
 */
package com.banlimusic.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.banlimusic.app.core.database.dao.LocalMusicDao
import com.banlimusic.app.core.database.entity.LocalTrackEntity
import com.banlimusic.app.core.model.Album
import com.banlimusic.app.core.model.Artist
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Scans the platform media library and persists imported tracks. Local audio is
 * represented by a normal [Track] whose source has [TrackSource.localUri] set.
 */
class LocalMusicRepository(
    private val context: Context,
    private val dao: LocalMusicDao,
    private val json: Json,
) {

    fun observeTracks(): Flow<List<Track>> = dao.observeAll().map { entities ->
        entities.mapNotNull(::decode)
    }

    suspend fun importAll(): Int = withContext(Dispatchers.IO) {
        val entities = queryMediaStore()
        if (entities.isNotEmpty()) dao.upsertAll(entities)
        entities.size
    }

    /** Imports explicitly picked files (SAF) after validating they look like songs. */
    suspend fun importFromUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val entities = uris.distinct().mapNotNull { uri -> readEntity(uri) }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
        entities.size
    }

    /** Recursively imports audio documents from a picked folder (SAF tree). */
    suspend fun importFromTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()
        runCatching {
            collectDocumentUris(treeUri, DocumentsContract.getTreeDocumentId(treeUri), uris)
        }
        importFromUrisUnsafe(uris)
    }

    suspend fun delete(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        dao.deleteAll(trackIds.toList())
    }

    suspend fun clear() = dao.clear()

    private suspend fun importFromUrisUnsafe(uris: List<Uri>): Int {
        val entities = uris.distinct().mapNotNull { uri -> readEntity(uri) }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
        return entities.size
    }

    private fun collectDocumentUris(treeUri: Uri, documentId: String, sink: MutableList<Uri>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val mimeIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex).orEmpty()
                val mime = cursor.getString(mimeIndex).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    runCatching { collectDocumentUris(treeUri, childId, sink) }
                } else if (looksLikeAudio(name, mime)) {
                    sink += DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                }
            }
        }
    }

    private fun readEntity(uri: Uri): LocalTrackEntity? {
        val resolver = context.contentResolver
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()
        val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        if (!looksLikeAudio(displayName, mime)) return null

        val retriever = MediaMetadataRetriever()
        val duration: Long
        val title: String
        val artist: String
        val album: String?
        try {
            retriever.setDataSource(context, uri)
            duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            title = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.').ifBlank { "未知歌曲" }
            artist = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: "未知歌手"
            album = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
        // Files shorter than the threshold are ringtones, notifications or clips,
        // not songs, so they are skipped instead of polluting the library.
        if (duration < MIN_SONG_DURATION_MS) return null

        return buildEntity(
            uriString = uri.toString(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
        )
    }

    private fun buildEntity(
        uriString: String,
        title: String,
        artist: String,
        album: String?,
        duration: Long,
    ): LocalTrackEntity {
        val track = Track(
            id = "local:$uriString",
            title = title,
            artists = listOf(Artist(name = artist)),
            album = album?.let { Album(title = it) },
            durationMs = duration,
            sources = listOf(
                TrackSource(
                    providerId = "local",
                    providerTrackId = uriString,
                    availableActions = setOf(TrackAction.STREAM),
                    localUri = uriString,
                    durationMs = duration,
                ),
            ),
        )
        return LocalTrackEntity(
            trackId = track.id,
            trackJson = json.encodeToString(Track.serializer(), track),
            uri = uriString,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun looksLikeAudio(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    private fun queryMediaStore(): List<LocalTrackEntity> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val now = System.currentTimeMillis()
        return buildList {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val title = cursor.getString(titleColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameColumn)?.substringBeforeLast('.').orEmpty()
                    if (title.isBlank()) continue
                    val artist = cursor.getString(artistColumn)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: "未知歌手"
                    val album = cursor.getString(albumColumn)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    val duration = cursor.getLong(durationColumn).coerceAtLeast(0L)
                    // Ignore ringtones, notification sounds and other short clips.
                    if (duration < MIN_SONG_DURATION_MS) continue
                    add(
                        buildEntity(
                            uriString = uri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                        ).copy(addedAt = now),
                    )
                }
            }
        }
    }

    private fun decode(entity: LocalTrackEntity): Track? = runCatching {
        json.decodeFromString(Track.serializer(), entity.trackJson)
    }.getOrNull()

    private companion object {
        const val MIN_SONG_DURATION_MS = 30_000L
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ape")
    }
}
