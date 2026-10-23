package com.banlimusic.app.core.cache

import android.content.Context
import java.io.File

/**
 * Owns the app cache directories (OkHttp, Coil, temporary downloads). Settings can
 * cap the total size; trimming removes the oldest files first.
 */
class CacheManager(private val context: Context) {

    fun sizeBytes(): Long = cacheRoots().sumOf(::directorySize)

    fun trimTo(maxBytes: Long) {
        val files = cacheRoots()
            .flatMap(::filesIn)
            .sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files) {
            if (total <= maxBytes) break
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                total -= length
            }
        }
    }

    fun clear() {
        cacheRoots().forEach { root ->
            root.listFiles()?.forEach { child ->
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun cacheRoots(): List<File> =
        listOfNotNull(context.cacheDir, context.externalCacheDir)
            .distinctBy { it.absolutePath }
            .filter { it.exists() }

    private fun filesIn(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile }
            .toList()

    private fun directorySize(root: File): Long =
        root.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
}
