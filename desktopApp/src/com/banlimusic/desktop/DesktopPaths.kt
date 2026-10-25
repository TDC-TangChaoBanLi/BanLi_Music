package com.banlimusic.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Platform-specific data locations for the desktop client. */
object DesktopPaths {

    private val home: Path = Paths.get(System.getProperty("user.home"))
    private val osName: String = System.getProperty("os.name").lowercase()

    private fun envDir(name: String): Path? =
        System.getenv(name)?.takeIf { it.isNotBlank() }?.let(Paths::get)

    val configDir: Path = when {
        osName.contains("win") -> envDir("APPDATA")
            ?.resolve("BanLiMusic")
            ?: home.resolve("AppData").resolve("Roaming").resolve("BanLiMusic")

        osName.contains("mac") -> home
            .resolve("Library")
            .resolve("Application Support")
            .resolve("BanLiMusic")

        else -> envDir("XDG_CONFIG_HOME")
            ?.resolve("banlimusic")
            ?: home.resolve(".config").resolve("banlimusic")
    }

    val cacheDir: Path = when {
        osName.contains("win") -> envDir("LOCALAPPDATA")
            ?.resolve("BanLiMusic")
            ?.resolve("cache")
            ?: configDir.resolve("cache")

        osName.contains("mac") -> home
            .resolve("Library")
            .resolve("Caches")
            .resolve("BanLiMusic")

        else -> envDir("XDG_CACHE_HOME")
            ?.resolve("banlimusic")
            ?: home.resolve(".cache").resolve("banlimusic")
    }

    val downloadDir: Path = home.resolve("Music").resolve("BanLiMusic")

    val libraryFile: Path get() = configDir.resolve("library.json")

    fun ensureDirectories() {
        listOf(configDir, cacheDir, downloadDir).forEach { dir ->
            runCatching { Files.createDirectories(dir) }
        }
    }
}
