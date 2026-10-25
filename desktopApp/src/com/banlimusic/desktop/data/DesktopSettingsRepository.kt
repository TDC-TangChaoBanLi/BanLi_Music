package com.banlimusic.desktop.data

import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.GdProviderSettings
import com.banlimusic.app.core.datastore.MAX_API_LIMIT_PER_WINDOW
import com.banlimusic.app.core.datastore.ProviderSettings
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.datastore.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * Desktop settings live in the platform user preferences store. The Android app
 * uses DataStore; both expose the same shared [AppSettings] model so provider
 * code can stay platform independent.
 */
class DesktopSettingsRepository {

    private val prefs = Preferences.userRoot().node("com/banlimusic/desktop")
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun setThemeMode(mode: ThemeMode) = update {
        prefs.put(KEY_THEME, mode.name)
    }

    fun setSourceEnabled(sourceKey: String, enabled: Boolean) = update {
        val known = SUPPORTED_SOURCES.map { it.id }.toSet()
        val current = enabledSources().toMutableSet()
        if (enabled) current += sourceKey else current -= sourceKey
        prefs.put(
            KEY_SOURCES,
            current.filter { it in known }.ifEmpty { setOf("netease") }.joinToString(","),
        )
    }

    fun setPlaybackQuality(qualityKbps: Int) = update {
        prefs.putInt(KEY_QUALITY, qualityKbps.coerceToDesktopQuality())
    }

    fun setSearchPageSize(size: Int) = update {
        prefs.putInt(KEY_PAGE_SIZE, size.coerceIn(10, 50))
    }

    fun setApiLimit(limit: Int) = update {
        prefs.putInt(KEY_API_LIMIT, limit.coerceIn(5, MAX_API_LIMIT_PER_WINDOW))
    }

    fun setDownloadQuality(qualityKbps: Int) = update {
        prefs.putInt(KEY_DOWNLOAD_QUALITY, qualityKbps.coerceToDesktopQuality())
    }

    fun setDownloadLyrics(enabled: Boolean) = update {
        prefs.putBoolean(KEY_DOWNLOAD_LYRICS, enabled)
    }

    fun setDownloadArtwork(enabled: Boolean) = update {
        prefs.putBoolean(KEY_DOWNLOAD_ARTWORK, enabled)
    }

    fun setRequestListArtwork(enabled: Boolean) = update {
        prefs.putBoolean(KEY_LIST_ARTWORK, enabled)
    }

    fun setDownloadDirectory(path: String) = update {
        prefs.put(KEY_DOWNLOAD_DIR, path.trim())
    }

    fun setAgreementAccepted(accepted: Boolean) = update {
        prefs.putBoolean(KEY_AGREEMENT, accepted)
    }

    private fun enabledSources(): Set<String> {
        val known = SUPPORTED_SOURCES.map { it.id }.toSet()
        val stored = prefs.get(KEY_SOURCES, "netease")
            .split(',')
            .map { it.trim() }
            .filter { it in known }
            .toSet()
        return stored.ifEmpty { setOf("netease") }
    }

    private fun read(): AppSettings {
        val theme = runCatching {
            ThemeMode.valueOf(prefs.get(KEY_THEME, ThemeMode.SYSTEM.name))
        }.getOrDefault(ThemeMode.SYSTEM)
        val gd = GdProviderSettings(
            enabledSourceKeys = enabledSources(),
            playbackQualityKbps = prefs.getInt(KEY_QUALITY, 320)
                .coerceToDesktopQuality(),
            apiLimitPerWindow = prefs.getInt(KEY_API_LIMIT, 45)
                .coerceIn(5, MAX_API_LIMIT_PER_WINDOW),
            searchPageSize = prefs.getInt(KEY_PAGE_SIZE, 20).coerceIn(10, 50),
        )
        return AppSettings(
            themeMode = theme,
            providers = ProviderSettings(gd = gd),
            agreementAccepted = prefs.getBoolean(KEY_AGREEMENT, false),
            requestListArtwork = prefs.getBoolean(KEY_LIST_ARTWORK, true),
            downloadDirectory = prefs.get(KEY_DOWNLOAD_DIR, ""),
            downloadQualityKbps = prefs.getInt(KEY_DOWNLOAD_QUALITY, 320)
                .coerceToDesktopQuality(),
            downloadLyrics = prefs.getBoolean(KEY_DOWNLOAD_LYRICS, false),
            downloadArtwork = prefs.getBoolean(KEY_DOWNLOAD_ARTWORK, false),
        )
    }

    private fun update(block: () -> Unit) {
        block()
        runCatching { prefs.flush() }
        _settings.value = read()
    }

    private fun Int.coerceToDesktopQuality(): Int =
        if (this in DESKTOP_QUALITIES) this else 320

    companion object {
        /** JLayer decodes MP3 only; lossless choices stay Android-only for now. */
        val DESKTOP_QUALITIES = listOf(128, 192, 320)

        private const val KEY_THEME = "theme_mode"
        private const val KEY_SOURCES = "enabled_sources"
        private const val KEY_QUALITY = "playback_quality_kbps"
        private const val KEY_PAGE_SIZE = "search_page_size"
        private const val KEY_API_LIMIT = "api_limit"
        private const val KEY_LIST_ARTWORK = "request_list_artwork"
        private const val KEY_DOWNLOAD_DIR = "download_directory"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality_kbps"
        private const val KEY_DOWNLOAD_LYRICS = "download_lyrics"
        private const val KEY_DOWNLOAD_ARTWORK = "download_artwork"
        private const val KEY_AGREEMENT = "agreement_accepted"
    }
}
