package com.banlimusic.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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

    suspend fun clear() = dao.clear()

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
                    val track = Track(
                        id = "local:$uri",
                        title = title,
                        artists = listOf(Artist(name = artist)),
                        album = album?.let { Album(title = it) },
                        durationMs = duration,
                        sources = listOf(
                            TrackSource(
                                providerId = "local",
                                providerTrackId = uri.toString(),
                                availableActions = setOf(TrackAction.STREAM),
                                localUri = uri.toString(),
                                durationMs = duration,
                            ),
                        ),
                    )
                    add(
                        LocalTrackEntity(
                            trackId = track.id,
                            trackJson = json.encodeToString(Track.serializer(), track),
                            uri = uri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            addedAt = now,
                        ),
                    )
                }
            }
        }
    }

    private fun decode(entity: LocalTrackEntity): Track? = runCatching {
        json.decodeFromString(Track.serializer(), entity.trackJson)
    }.getOrNull()
}
