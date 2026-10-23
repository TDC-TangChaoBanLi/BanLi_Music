package com.banlimusic.app.domain.playback

import android.net.Uri
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import java.util.Base64

/**
 * The media item URI contains every candidate source, so ExoPlayer can resolve a
 * playable URL lazily (one API request at playback time, not per search result).
 */
data class StreamCandidate(
    val providerId: String,
    val providerTrackId: String,
)

object StreamUriCodec {
    const val SCHEME = "banli"
    const val HOST = "stream"
    private const val FIELD_SEPARATOR = "|"
    private const val SOURCE_SEPARATOR = ";"

    fun toUri(candidates: List<StreamCandidate>, mediaId: String): Uri {
        val encoded = encode(candidates)
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(encoded)
            .appendQueryParameter("id", mediaId)
            .build()
    }

    fun encode(candidates: List<StreamCandidate>): String {
        val raw = candidates.joinToString(SOURCE_SEPARATOR) {
            "${escape(it.providerId)}$FIELD_SEPARATOR${escape(it.providerTrackId)}"
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    fun decode(encoded: String): List<StreamCandidate> = runCatching {
        val raw = String(Base64.getUrlDecoder().decode(encoded))
        raw.split(SOURCE_SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD_SEPARATOR)
            if (fields.size != 2) return@mapNotNull null
            val providerId = unescape(fields[0])
            val trackId = unescape(fields[1])
            if (providerId.isBlank() || trackId.isBlank()) null
            else StreamCandidate(providerId, trackId)
        }
    }.getOrDefault(emptyList())

    fun candidatesOf(source: TrackSource): List<StreamCandidate> =
        if (TrackAction.STREAM in source.availableActions) {
            listOf(StreamCandidate(source.providerId, source.providerTrackId))
        } else {
            emptyList()
        }

    private fun escape(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun unescape(value: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(value))
    }.getOrDefault("")
}
