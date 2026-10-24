package com.banlimusic.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.network.JsonHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes widget artwork to a small bitmap. Rendering only reads the in-memory
 * or on-disk cache; downloading/copying is performed in the background so a
 * slow network request can never stall widget progress updates.
 */
object WidgetArtworkStore {
    private const val MAX_AGE_MS = 6 * 60 * 60_000L
    private const val MAX_EDGE_PX = 256
    private const val FILE_PREFIX = "widget_artwork_"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val memory = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > 4
    }

    fun loadCached(context: Context, artworkUrl: String): Bitmap? {
        if (artworkUrl.isBlank()) return null
        synchronized(memory) { memory[artworkUrl] }?.let { return it }
        val file = File(context.cacheDir, "$FILE_PREFIX${key(artworkUrl)}.jpg")
        if (!file.exists()) return null
        val bitmap = runCatching { decodeSmall(file) }.getOrNull() ?: return null
        synchronized(memory) { memory[artworkUrl] = bitmap }
        return bitmap
    }

    /** Starts a one-shot background load and refreshes the widget on success. */
    fun request(context: Context, artworkUrl: String) {
        if (artworkUrl.isBlank()) return
        if (!inFlight.add(artworkUrl)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val file = File(appContext.cacheDir, "$FILE_PREFIX${key(artworkUrl)}.jpg")
                val fresh = file.exists() &&
                    System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
                val ready = if (fresh) {
                    true
                } else {
                    withTimeoutOrNull(20_000L) {
                        runCatching { copyToFile(appContext, artworkUrl, file) }.getOrDefault(false)
                    } ?: false
                }
                if (ready && file.exists()) {
                    decodeSmall(file)?.let { bitmap ->
                        synchronized(memory) { memory[artworkUrl] = bitmap }
                        cleanup(appContext, file)
                        PlayerWidgetUpdater.refresh(appContext)
                    }
                }
            } finally {
                inFlight.remove(artworkUrl)
            }
        }
    }

    private suspend fun copyToFile(context: Context, artworkUrl: String, target: File): Boolean =
        withContext(Dispatchers.IO) {
            val input: InputStream? = when {
                artworkUrl.startsWith("content://") ->
                    context.contentResolver.openInputStream(Uri.parse(artworkUrl))

                artworkUrl.startsWith("file://") ->
                    artworkUrl.substringAfter("file://").let { path ->
                        File(Uri.decode(path)).takeIf { it.exists() }?.inputStream()
                    }

                else -> {
                    val app = context.applicationContext as? MusicApplication ?: return@withContext false
                    val request = Request.Builder()
                        .url(artworkUrl)
                        .header("User-Agent", JsonHttpClient.USER_AGENT)
                        .build()
                    val response = runCatching {
                        app.container.downloadHttpClient.newCall(request).execute()
                    }.getOrNull() ?: return@withContext false
                    if (!response.isSuccessful) {
                        response.close()
                        return@withContext false
                    }
                    return@withContext response.use { body ->
                        body.body.byteStream().use { source ->
                            target.outputStream().use { output -> source.copyTo(output) }
                        }
                        true
                    }
                }
            }
            input ?: return@withContext false
            return@withContext input.use { source ->
                target.outputStream().use { output -> source.copyTo(output) }
                true
            }
        }

    private fun decodeSmall(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE_PX || bounds.outHeight / sample > MAX_EDGE_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun cleanup(context: Context, keep: File) {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it != keep }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun key(value: String): String =
        Integer.toHexString(value.hashCode()) + value.length.toString(16)
}
