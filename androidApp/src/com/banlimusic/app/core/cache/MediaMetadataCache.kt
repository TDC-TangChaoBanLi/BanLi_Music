/**
 * 文件作用：歌词与海报的磁盘缓存，并提供并发安全的海报写入。
 */
package com.banlimusic.app.core.cache

import android.content.Context
import android.net.Uri
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.network.JsonHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * On-disk cache for lyrics and artwork so they survive process restarts and stay
 * available when the provider is temporarily unreachable. Files live under the app
 * cache directory, which the settings' cache cleanup already removes.
 */
class MediaMetadataCache(
    private val context: Context,
    private val json: Json,
    private val httpClient: OkHttpClient,
) {
    private val lyricsDir = File(context.cacheDir, "lyrics").apply { mkdirs() }
    private val artworkDir = File(context.cacheDir, "artwork").apply { mkdirs() }
    private val artworkMutex = Mutex()

    suspend fun lyrics(trackId: String): Lyrics? = withContext(Dispatchers.IO) {
        val file = File(lyricsDir, "${key(trackId)}.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString(Lyrics.serializer(), file.readText())
        }.getOrNull()
    }

    suspend fun putLyrics(trackId: String, lyrics: Lyrics) = withContext(Dispatchers.IO) {
        runCatching {
            File(lyricsDir, "${key(trackId)}.json")
                .writeText(json.encodeToString(Lyrics.serializer(), lyrics))
        }
    }

    /** Returns a `file://` URI when the artwork has been cached. */
    suspend fun artwork(trackId: String): String? = withContext(Dispatchers.IO) {
        val file = File(artworkDir, "${key(trackId)}.img")
        if (file.exists() && file.length() > 0L) {
            Uri.fromFile(file).toString()
        } else {
            null
        }
    }

    /** Downloads [url] into the cache and returns the local URI, or null on failure. */
    suspend fun putArtwork(trackId: String, url: String): String? = withContext(Dispatchers.IO) {
        if (url.startsWith("file://") || url.startsWith("content://")) return@withContext url
        artworkMutex.withLock {
            // Another caller (player page or playback service) may have finished
            // writing this track's artwork while we waited for the lock.
            val file = File(artworkDir, "${key(trackId)}.img")
            if (file.exists() && file.length() > 0L) {
                return@withLock Uri.fromFile(file).toString()
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", JsonHttpClient.USER_AGENT)
                .build()
            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (file.length() > 0L) Uri.fromFile(file).toString() else null
                }
            }.getOrNull()
        }
    }

    private fun key(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
