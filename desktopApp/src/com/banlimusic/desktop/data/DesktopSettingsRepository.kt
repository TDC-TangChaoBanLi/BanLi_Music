package com.banlimusic.desktop.data

import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.GdProviderSettings
import com.banlimusic.app.core.datastore.MAX_API_LIMIT_PER_WINDOW
import com.banlimusic.app.core.datastore.ProviderSettings
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.datastore.SyncSettings
import com.banlimusic.app.core.datastore.TabletMiniPlayerPosition
import com.banlimusic.app.core.datastore.TabletNavPosition
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.datastore.sanitizeNavItems
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

    fun setLocalMusicDirectory(path: String) = update {
        prefs.put(KEY_LOCAL_MUSIC_DIR, path.trim())
    }

    fun setAgreementAccepted(accepted: Boolean) = update {
        prefs.putBoolean(KEY_AGREEMENT, accepted)
    }

    fun setDynamicColor(enabled: Boolean) = update {
        prefs.putBoolean(KEY_DYNAMIC_COLOR, enabled)
    }

    fun setCustomThemeColor(color: Int?) = update {
        if (color == null) prefs.remove(KEY_CUSTOM_COLOR) else prefs.putInt(KEY_CUSTOM_COLOR, color)
    }

    fun setBottomNavItems(items: List<String>) = update {
        prefs.put(KEY_NAV_ITEMS, sanitizeNavItems(items).joinToString(","))
    }

    fun setDefaultNavItem(item: String) = update {
        prefs.put(KEY_DEFAULT_NAV, item)
    }

    fun setTabletNavPosition(position: TabletNavPosition) = update {
        prefs.put(KEY_NAV_POSITION, position.name)
    }

    fun setTabletMiniPlayerPosition(position: TabletMiniPlayerPosition) = update {
        prefs.put(KEY_MINI_POSITION, position.name)
    }

    fun setOpenPlayerOnTrackClick(enabled: Boolean) = update {
        prefs.putBoolean(KEY_OPEN_PLAYER, enabled)
    }

    fun setDefaultShowLyrics(enabled: Boolean) = update {
        prefs.putBoolean(KEY_DEFAULT_LYRICS, enabled)
    }

    fun setAllowMobileNetwork(enabled: Boolean) = update {
        prefs.putBoolean(KEY_ALLOW_MOBILE, enabled)
    }

    fun setPlaybackRetryCount(count: Int) = update {
        prefs.putInt(KEY_RETRY_COUNT, count.coerceIn(0, 10))
    }

    fun setConsecutiveFailureLimit(limit: Int) = update {
        prefs.putInt(KEY_FAILURE_LIMIT, limit.coerceIn(1, 10))
    }

    fun setAutoSwitchPlaybackSource(enabled: Boolean) = update {
        prefs.putBoolean(KEY_AUTO_SWITCH, enabled)
    }

    fun setAutoLoadArtwork(enabled: Boolean) = update {
        prefs.putBoolean(KEY_AUTO_ARTWORK, enabled)
    }

    fun setCacheMaxBytes(bytes: Long) = update {
        prefs.putLong(KEY_CACHE_MAX, bytes.coerceAtLeast(0L))
    }

    fun setDefaultFavoriteCategory(category: String) = update {
        prefs.put(KEY_DEFAULT_CATEGORY, category.trim().ifBlank {
            com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY
        })
    }

    fun setAutoImportLocalMusic(enabled: Boolean) = update {
        prefs.putBoolean(KEY_AUTO_IMPORT, enabled)
    }

    fun setHighRefreshRateLists(enabled: Boolean) = update {
        prefs.putBoolean(KEY_HIGH_REFRESH, enabled)
    }

    fun setWidgetTheme(theme: WidgetTheme) = update {
        prefs.put(KEY_WIDGET_THEME, theme.name)
    }

    fun setDesktopFloatingLyrics(enabled: Boolean) = update {
        prefs.putBoolean(KEY_FLOATING_LYRICS, enabled)
    }

    fun setSyncAutoSync(enabled: Boolean) = update {
        prefs.putBoolean(KEY_SYNC_AUTO, enabled)
    }

    fun setSyncIntervalHours(hours: Int) = update {
        prefs.putInt(
            KEY_SYNC_INTERVAL,
            hours.coerceIn(
                com.banlimusic.app.core.datastore.MIN_SYNC_INTERVAL_HOURS,
                com.banlimusic.app.core.datastore.MAX_SYNC_INTERVAL_HOURS,
            ),
        )
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
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            customThemeColor = prefs.get(KEY_CUSTOM_COLOR, null)?.toIntOrNull(),
            providers = ProviderSettings(gd = gd),
            agreementAccepted = prefs.getBoolean(KEY_AGREEMENT, false),
            requestListArtwork = prefs.getBoolean(KEY_LIST_ARTWORK, false),
            downloadDirectory = prefs.get(KEY_DOWNLOAD_DIR, ""),
            downloadQualityKbps = prefs.getInt(KEY_DOWNLOAD_QUALITY, 320)
                .coerceToDesktopQuality(),
            downloadLyrics = prefs.getBoolean(KEY_DOWNLOAD_LYRICS, false),
            downloadArtwork = prefs.getBoolean(KEY_DOWNLOAD_ARTWORK, false),
            localMusicDirectory = prefs.get(KEY_LOCAL_MUSIC_DIR, ""),
            autoLoadArtwork = prefs.getBoolean(KEY_AUTO_ARTWORK, true),
            cacheMaxBytes = prefs.getLong(
                KEY_CACHE_MAX,
                com.banlimusic.app.core.datastore.DEFAULT_CACHE_MAX_BYTES,
            ).coerceAtLeast(0L),
            bottomNavItems = prefs.get(KEY_NAV_ITEMS, "")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .let { if (it.isEmpty()) AppSettings().bottomNavItems else sanitizeNavItems(it) },
            defaultNavItem = prefs.get(KEY_DEFAULT_NAV, "home"),
            tabletNavPosition = runCatching {
                TabletNavPosition.valueOf(prefs.get(KEY_NAV_POSITION, TabletNavPosition.LEFT.name))
            }.getOrDefault(TabletNavPosition.LEFT),
            tabletMiniPlayerPosition = runCatching {
                TabletMiniPlayerPosition.valueOf(
                    prefs.get(KEY_MINI_POSITION, TabletMiniPlayerPosition.BOTTOM.name),
                )
            }.getOrDefault(TabletMiniPlayerPosition.BOTTOM),
            openPlayerOnTrackClick = prefs.getBoolean(KEY_OPEN_PLAYER, true),
            defaultShowLyrics = prefs.getBoolean(KEY_DEFAULT_LYRICS, false),
            allowMobileNetwork = prefs.getBoolean(KEY_ALLOW_MOBILE, true),
            playbackRetryCount = prefs.getInt(KEY_RETRY_COUNT, 3).coerceIn(0, 10),
            consecutiveFailureLimit = prefs.getInt(KEY_FAILURE_LIMIT, 3).coerceIn(1, 10),
            autoSwitchPlaybackSource = prefs.getBoolean(KEY_AUTO_SWITCH, true),
            defaultFavoriteCategory = prefs.get(
                KEY_DEFAULT_CATEGORY,
                com.banlimusic.app.core.datastore.DEFAULT_FAVORITE_CATEGORY,
            ),
            autoImportLocalMusic = prefs.getBoolean(KEY_AUTO_IMPORT, false),
            highRefreshRateLists = prefs.getBoolean(KEY_HIGH_REFRESH, true),
            widgetTheme = runCatching {
                WidgetTheme.valueOf(prefs.get(KEY_WIDGET_THEME, WidgetTheme.SYSTEM.name))
            }.getOrDefault(WidgetTheme.SYSTEM),
            desktopFloatingLyrics = prefs.getBoolean(KEY_FLOATING_LYRICS, false),
            sync = SyncSettings(
                autoSync = prefs.getBoolean(KEY_SYNC_AUTO, false),
                intervalHours = prefs.getInt(KEY_SYNC_INTERVAL, 6).coerceIn(
                    com.banlimusic.app.core.datastore.MIN_SYNC_INTERVAL_HOURS,
                    com.banlimusic.app.core.datastore.MAX_SYNC_INTERVAL_HOURS,
                ),
            ),
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
        private const val KEY_LOCAL_MUSIC_DIR = "local_music_directory"
        private const val KEY_AGREEMENT = "agreement_accepted"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_CUSTOM_COLOR = "custom_theme_color"
        private const val KEY_NAV_ITEMS = "bottom_nav_items"
        private const val KEY_DEFAULT_NAV = "default_nav_item"
        private const val KEY_NAV_POSITION = "tablet_nav_position"
        private const val KEY_MINI_POSITION = "tablet_mini_player_position"
        private const val KEY_OPEN_PLAYER = "open_player_on_track_click"
        private const val KEY_DEFAULT_LYRICS = "default_show_lyrics"
        private const val KEY_ALLOW_MOBILE = "allow_mobile_network"
        private const val KEY_RETRY_COUNT = "playback_retry_count"
        private const val KEY_FAILURE_LIMIT = "consecutive_failure_limit"
        private const val KEY_AUTO_SWITCH = "auto_switch_source"
        private const val KEY_AUTO_ARTWORK = "auto_load_artwork"
        private const val KEY_CACHE_MAX = "cache_max_bytes"
        private const val KEY_DEFAULT_CATEGORY = "default_favorite_category"
        private const val KEY_AUTO_IMPORT = "auto_import_local_music"
        private const val KEY_WIDGET_THEME = "widget_theme"
        private const val KEY_HIGH_REFRESH = "high_refresh_lists"
        private const val KEY_FLOATING_LYRICS = "desktop_floating_lyrics"
        private const val KEY_SYNC_AUTO = "sync_auto"
        private const val KEY_SYNC_INTERVAL = "sync_interval_hours"
    }
}
