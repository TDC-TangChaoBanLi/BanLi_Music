package com.banlimusic.app.player

import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.domain.playback.StreamCandidate
import com.banlimusic.app.domain.playback.StreamUriCodec
import kotlinx.serialization.json.Json

object TrackMediaItems {
    const val EXTRA_TRACK_JSON = "com.banlimusic.app.TRACK_JSON"

    fun create(track: Track, json: Json): MediaItem {
        val candidates = track.sources
            .filter { TrackAction.STREAM in it.availableActions }
            .map { StreamCandidate(it.providerId, it.providerTrackId) }
        val uri = if (candidates.isEmpty()) {
            Uri.EMPTY
        } else {
            StreamUriCodec.toUri(candidates, track.id)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistLine)
            .setAlbumTitle(track.album?.title)
            .setArtworkUri(track.artworkUrl?.toUri() ?: track.album?.artworkUrl?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                putString(EXTRA_TRACK_JSON, json.encodeToString(Track.serializer(), track))
            })
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun createLocal(track: Track, fileUri: Uri, json: Json): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistLine)
            .setAlbumTitle(track.album?.title)
            .setArtworkUri(track.artworkUrl?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                putString(EXTRA_TRACK_JSON, json.encodeToString(Track.serializer(), track))
            })
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(fileUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun trackOf(item: MediaItem?, json: Json): Track? {
        val raw = item?.mediaMetadata?.extras?.getString(EXTRA_TRACK_JSON) ?: return null
        return runCatching { json.decodeFromString(Track.serializer(), raw) }.getOrNull()
    }

    fun withArtwork(item: MediaItem, artworkUrl: String): MediaItem {
        val metadata = item.mediaMetadata.buildUpon()
            .setArtworkUri(artworkUrl.toUri())
            .build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }
}
