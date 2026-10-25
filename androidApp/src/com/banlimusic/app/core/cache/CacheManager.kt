/**
 * 文件作用：管理非音频缓存目录，统计各类缓存大小并执行清理。
 */
package com.banlimusic.app.core.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import java.io.File

data class CacheUsage(
    val totalBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val artworkBytes: Long = 0L,
    val lyricsBytes: Long = 0L,
    val otherBytes: Long = 0L,
)

/**
 * Owns non-audio app cache directories (OkHttp, Coil, widget artwork, temporary
 * downloads) and reports per-category sizes. Audio is owned by Media3's
 * [PlaybackCache] and trimmed through its application-level index.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class CacheManager(
    private val context: Context,
    private val playbackCache: PlaybackCache,
) {

    fun usage(): CacheUsage {
        val total = cacheRoots().sumOf(::directorySize)
        val audio = runCatching { playbackCache.cache.cacheSpace }.getOrDefault(0L)
        val artwork = directorySize(artworkDir())
        val lyrics = directorySize(lyricsDir())
        return CacheUsage(
            totalBytes = total,
            audioBytes = audio.coerceAtMost(total),
            artworkBytes = artwork,
            lyricsBytes = lyrics,
            otherBytes = (total - audio - artwork - lyrics).coerceAtLeast(0L),
        )
    }

    /**
     * Deletes the oldest non-audio files until the total cache falls below the
     * requested cap. Audio trimming is coordinated by CacheRepository because it
     * must go through SimpleCache.removeResource.
     */
    fun trimNonAudioTo(maxBytes: Long) {
        val files = cacheRoots()
            .flatMap(::filesIn)
            .filterNot { isManagedAudio(it) || isActiveDownload(it) }
            .sortedBy { it.lastModified() }
        var total = cacheRoots().sumOf(::directorySize)
        if (total <= maxBytes) return
        for (file in files) {
            if (total <= maxBytes) break
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) total -= length
        }
    }

    fun clearArtwork() {
        clearDirectory(artworkDir())
    }

    fun clearLyrics() {
        clearDirectory(lyricsDir())
    }

    /**
     * Clears HTTP, image and widget caches. Active download `.part` files and
     * the managed audio/artwork/lyrics directories are intentionally kept.
     */
    fun clearOther() {
        cacheRoots().forEach { root ->
            root.listFiles()?.forEach { child ->
                if (child.name in MANAGED_DIRS || child.name == "downloads") return@forEach
                runCatching { child.deleteRecursively() }
            }
        }
    }

    fun clearNonAudio() {
        clearArtwork()
        clearLyrics()
        clearOther()
    }

    private fun clearDirectory(directory: File) {
        directory.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
    }

    private fun artworkDir(): File = File(context.cacheDir, "artwork")

    private fun lyricsDir(): File = File(context.cacheDir, "lyrics")

    private fun cacheRoots(): List<File> =
        listOfNotNull(context.cacheDir, context.externalCacheDir)
            .distinctBy { it.absolutePath }
            .filter { it.exists() }

    private fun filesIn(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile }
            .toList()

    private fun directorySize(root: File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun isManagedAudio(file: File): Boolean =
        file.absolutePath.startsWith(File(context.cacheDir, "playback-media").absolutePath)

    private fun isActiveDownload(file: File): Boolean =
        file.absolutePath.startsWith(File(context.cacheDir, "downloads").absolutePath)

    private companion object {
        val MANAGED_DIRS = setOf("playback-media", "artwork", "lyrics")
    }
}
