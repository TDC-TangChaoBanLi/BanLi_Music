package com.banlimusic.desktop.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.desktop.data.DesktopMetadataCache
import com.banlimusic.desktop.data.DesktopMusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/** Small memory/disk-backed artwork loader so desktop rows do not need Coil. */
class DesktopArtworkLoader(
    private val repository: DesktopMusicRepository,
    private val metadataCache: DesktopMetadataCache,
    private val httpClient: OkHttpClient,
) {
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    fun cached(trackId: String): ImageBitmap? = memory[trackId]

    suspend fun load(track: Track, allowRemote: Boolean = true): ImageBitmap? {
        memory[track.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val local = metadataCache.artwork(track.id)
                val source = local ?: if (allowRemote) repository.artwork(track) else null
                if (source.isNullOrBlank()) {
                    return@withContext null
                }
                val bytes = if (source.startsWith("file:")) {
                    Files.readAllBytes(Paths.get(URI(source)))
                } else if (source.startsWith("http://") || source.startsWith("https://")) {
                    fetch(source)
                } else {
                    null
                }
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext null
                }
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                memory[track.id] = bitmap
                if (memory.size > 256) {
                    memory.keys.take(memory.size - 256).forEach(memory::remove)
                }
                bitmap
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun clearMemory() {
        memory.clear()
    }

    private fun fetch(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", JsonHttpClient.USER_AGENT)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body.bytes()
        }
    }
}
