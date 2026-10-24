package com.banlimusic.app.domain.playback

import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The media item URI contains every candidate source, so ExoPlayer can resolve a
 * playable URL lazily (one API request at playback time, not per search result).
 *
 * This codec is platform independent: Android converts the returned string with
 * `Uri.parse`, desktop can use it directly or parse it with java.net.URI.
 */
@OptIn(ExperimentalEncodingApi::class)
object StreamUriCodec {
    const val SCHEME = "banli"
    const val HOST = "stream"
    private const val FIELD_SEPARATOR = "|"
    private const val SOURCE_SEPARATOR = ";"
    private const val PREFIX = "$SCHEME://$HOST/"
    private const val CACHE_PREFIX = "banli:v1:"

    fun toUriString(candidates: List<StreamCandidate>, mediaId: String): String {
        val encoded = encode(candidates)
        return "$PREFIX$encoded?id=${encodeValue(mediaId)}"
    }

    fun encode(candidates: List<StreamCandidate>): String {
        val raw = candidates.joinToString(SOURCE_SEPARATOR) {
            "${encodeValue(it.providerId)}$FIELD_SEPARATOR${encodeValue(it.providerTrackId)}"
        }
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(raw.encodeToByteArray())
    }

    fun decode(encoded: String): List<StreamCandidate> = runCatching {
        val raw = decodeValue(encoded)
        raw.split(SOURCE_SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD_SEPARATOR)
            if (fields.size != 2) return@mapNotNull null
            val providerId = decodeValue(fields[0])
            val trackId = decodeValue(fields[1])
            if (providerId.isBlank() || trackId.isBlank()) null
            else StreamCandidate(providerId, trackId)
        }
    }.getOrDefault(emptyList())

    fun encodedOf(uri: String): String? = uri
        .takeIf { it.startsWith(PREFIX) }
        ?.removePrefix(PREFIX)
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }

    fun mediaIdOf(uri: String): String? {
        val raw = uri.substringAfter("?id=", "").substringBefore('&')
        if (raw.isBlank()) return null
        return runCatching { decodeValue(raw) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Stable cache key: independent from temporary provider URLs. */
    fun cacheKey(mediaId: String, qualityKbps: Int): String =
        "$CACHE_PREFIX$mediaId:$qualityKbps"

    fun trackCachePrefix(trackId: String): String = "$CACHE_PREFIX$trackId:"

    fun trackIdOfCacheKey(cacheKey: String): String? {
        if (!cacheKey.startsWith(CACHE_PREFIX)) return null
        val rest = cacheKey.removePrefix(CACHE_PREFIX)
        return rest.substringBeforeLast(':', missingDelimiterValue = "").takeIf { it.isNotBlank() }
    }

    fun candidatesOf(source: TrackSource): List<StreamCandidate> =
        if (TrackAction.STREAM in source.availableActions) {
            listOf(StreamCandidate(source.providerId, source.providerTrackId))
        } else {
            emptyList()
        }

    private fun encodeValue(value: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(value.encodeToByteArray())

    private fun decodeValue(value: String): String = Base64.UrlSafe
        .withPadding(Base64.PaddingOption.PRESENT)
        .decode(value.padBase64())
        .decodeToString()

    /** UrlSafe decoder accepts padded input; callers may store unpadded values. */
    private fun String.padBase64(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }
}

data class StreamCandidate(
    val providerId: String,
    val providerTrackId: String,
)
