package com.banlimusic.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WidgetTheme { SYSTEM, BLACK, WHITE }

const val DEFAULT_CACHE_MAX_BYTES = 200L * 1024L * 1024L
const val MIN_CACHE_MAX_BYTES = 50L * 1024L * 1024L
const val MAX_CACHE_MAX_BYTES = 2L * 1024L * 1024L * 1024L
const val DEFAULT_FAVORITE_CATEGORY = "默认收藏"
const val MAX_API_LIMIT_PER_WINDOW = 50
const val MIN_GD_REQUEST_INTERVAL_MS = 1_000L

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val qualityKbps: Int = 128,
    val enabledSources: Set<String> = setOf("netease"),
    val allowMobileNetwork: Boolean = true,
    val playbackRetryCount: Int = 3,
    val consecutiveFailureLimit: Int = 3,
    val defaultShowLyrics: Boolean = false,
    val searchPageSize: Int = 20,
    val apiLimitPerWindow: Int = 45,
    val downloadDirectory: String = "",
    val downloadQualityKbps: Int = 128,
    val downloadLyrics: Boolean = false,
    val downloadArtwork: Boolean = false,
    val cacheMaxBytes: Long = DEFAULT_CACHE_MAX_BYTES,
    val bottomNavItems: List<String> = DEFAULT_BOTTOM_NAV_ITEMS,
    val agreementAccepted: Boolean = false,
    val defaultFavoriteCategory: String = DEFAULT_FAVORITE_CATEGORY,
    val autoImportLocalMusic: Boolean = false,
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Requests a ~90Hz display mode while browsing track lists. */
    val highRefreshRateLists: Boolean = true,
    /** Opens the full player when a track row is tapped; disable to stay on the list. */
    val openPlayerOnTrackClick: Boolean = true,
)

val SUPPORTED_SOURCES = listOf(
    SourceOption("netease", "GD网易云"),
    SourceOption("joox", "GD JOOX"),
    SourceOption("bilibili", "GD B站"),
)

data class SourceOption(val id: String, val label: String)

data class NavOption(val id: String, val label: String)

val BOTTOM_NAV_OPTIONS = listOf(
    NavOption("home", "首页"),
    NavOption("search", "搜索"),
    NavOption("library", "音乐库"),
    NavOption("downloads", "下载"),
    NavOption("settings", "设置"),
)

/** Home sits in the middle of the default five-item navigation bar. */
val DEFAULT_BOTTOM_NAV_ITEMS = listOf("search", "library", "home", "downloads", "settings")

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val quality = intPreferencesKey("quality_kbps")
        val sources = stringSetPreferencesKey("enabled_sources")
        val allowMobileNetwork = booleanPreferencesKey("allow_mobile_network")
        val playbackRetryCount = intPreferencesKey("playback_retry_count")
        val consecutiveFailureLimit = intPreferencesKey("consecutive_failure_limit")
        val defaultShowLyrics = booleanPreferencesKey("default_show_lyrics")
        val searchPageSize = intPreferencesKey("search_page_size")
        val apiLimitPerWindow = intPreferencesKey("api_limit_per_window")
        val downloadDirectory = stringPreferencesKey("download_directory")
        val downloadQuality = intPreferencesKey("download_quality_kbps")
        val downloadLyrics = booleanPreferencesKey("download_lyrics")
        val downloadArtwork = booleanPreferencesKey("download_artwork")
        val cacheMaxBytes = longPreferencesKey("cache_max_bytes")
        val bottomNavItems = stringPreferencesKey("bottom_nav_items")
        val agreementAccepted = booleanPreferencesKey("agreement_accepted")
        val defaultFavoriteCategory = stringPreferencesKey("default_favorite_category")
        val autoImportLocalMusic = booleanPreferencesKey("auto_import_local_music")
        val widgetTheme = stringPreferencesKey("widget_theme")
        val highRefreshRateLists = booleanPreferencesKey("high_refresh_rate_lists")
        val openPlayerOnTrackClick = booleanPreferencesKey("open_player_on_track_click")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            AppSettings(
                themeMode = preferences[Keys.themeMode]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = preferences[Keys.dynamicColor] ?: true,
                qualityKbps = preferences[Keys.quality] ?: 128,
                enabledSources = preferences[Keys.sources] ?: setOf("netease"),
                allowMobileNetwork = preferences[Keys.allowMobileNetwork] ?: true,
                playbackRetryCount = (preferences[Keys.playbackRetryCount] ?: 3).coerceIn(0, 10),
                consecutiveFailureLimit = (preferences[Keys.consecutiveFailureLimit] ?: 3).coerceIn(1, 10),
                defaultShowLyrics = preferences[Keys.defaultShowLyrics] ?: false,
                searchPageSize = (preferences[Keys.searchPageSize] ?: 20).coerceIn(10, 50),
                apiLimitPerWindow = (preferences[Keys.apiLimitPerWindow] ?: 45)
                    .coerceIn(1, MAX_API_LIMIT_PER_WINDOW),
                downloadDirectory = preferences[Keys.downloadDirectory].orEmpty(),
                downloadQualityKbps = (preferences[Keys.downloadQuality] ?: 128)
                    .takeIf { it > 0 } ?: 128,
                downloadLyrics = preferences[Keys.downloadLyrics] ?: false,
                downloadArtwork = preferences[Keys.downloadArtwork] ?: false,
                cacheMaxBytes = (preferences[Keys.cacheMaxBytes] ?: DEFAULT_CACHE_MAX_BYTES)
                    .coerceIn(MIN_CACHE_MAX_BYTES, MAX_CACHE_MAX_BYTES),
                bottomNavItems = parseNavItems(preferences[Keys.bottomNavItems]),
                agreementAccepted = preferences[Keys.agreementAccepted] ?: false,
                defaultFavoriteCategory = preferences[Keys.defaultFavoriteCategory]
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_FAVORITE_CATEGORY,
                autoImportLocalMusic = preferences[Keys.autoImportLocalMusic] ?: false,
                widgetTheme = preferences[Keys.widgetTheme]
                    ?.let { runCatching { WidgetTheme.valueOf(it) }.getOrNull() }
                    ?: WidgetTheme.SYSTEM,
                highRefreshRateLists = preferences[Keys.highRefreshRateLists] ?: true,
                openPlayerOnTrackClick = preferences[Keys.openPlayerOnTrackClick] ?: true,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.settingsDataStore.edit {
        it[Keys.themeMode] = mode.name
    }

    suspend fun setDynamicColor(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.dynamicColor] = enabled
    }

    suspend fun setQuality(qualityKbps: Int) = context.settingsDataStore.edit {
        it[Keys.quality] = qualityKbps
    }

    suspend fun setSourceEnabled(source: String, enabled: Boolean) = context.settingsDataStore.edit { preferences ->
        val current = preferences[Keys.sources] ?: setOf("netease")
        val updated = if (enabled) current + source else current - source
        // Never leave the app without a music source.
        preferences[Keys.sources] = updated.ifEmpty { setOf("netease") }
    }

    suspend fun setAllowMobileNetwork(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.allowMobileNetwork] = enabled
    }

    suspend fun setPlaybackRetryCount(count: Int) = context.settingsDataStore.edit {
        it[Keys.playbackRetryCount] = count.coerceIn(0, 10)
    }

    suspend fun setConsecutiveFailureLimit(limit: Int) = context.settingsDataStore.edit {
        it[Keys.consecutiveFailureLimit] = limit.coerceIn(1, 10)
    }

    suspend fun setDefaultShowLyrics(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.defaultShowLyrics] = enabled
    }

    suspend fun setSearchPageSize(size: Int) = context.settingsDataStore.edit {
        it[Keys.searchPageSize] = size.coerceIn(10, 50)
    }

    suspend fun setApiLimitPerWindow(limit: Int) = context.settingsDataStore.edit {
        it[Keys.apiLimitPerWindow] = limit.coerceIn(1, MAX_API_LIMIT_PER_WINDOW)
    }

    suspend fun setDownloadDirectory(path: String) = context.settingsDataStore.edit {
        it[Keys.downloadDirectory] = path.trim()
    }

    suspend fun setDownloadQuality(qualityKbps: Int) = context.settingsDataStore.edit {
        it[Keys.downloadQuality] = if (qualityKbps > 0) qualityKbps else 0
    }

    suspend fun setDownloadLyrics(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.downloadLyrics] = enabled
    }

    suspend fun setDownloadArtwork(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.downloadArtwork] = enabled
    }

    suspend fun setCacheMaxBytes(bytes: Long) = context.settingsDataStore.edit {
        it[Keys.cacheMaxBytes] = bytes.coerceIn(MIN_CACHE_MAX_BYTES, MAX_CACHE_MAX_BYTES)
    }

    suspend fun setBottomNavItems(items: List<String>) = context.settingsDataStore.edit {
        it[Keys.bottomNavItems] = sanitizeNavItems(items).joinToString(",")
    }

    suspend fun setAgreementAccepted(accepted: Boolean) = context.settingsDataStore.edit {
        it[Keys.agreementAccepted] = accepted
    }

    suspend fun setDefaultFavoriteCategory(category: String) = context.settingsDataStore.edit {
        it[Keys.defaultFavoriteCategory] = category.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }
    }

    suspend fun setAutoImportLocalMusic(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.autoImportLocalMusic] = enabled
    }

    suspend fun setWidgetTheme(theme: WidgetTheme) = context.settingsDataStore.edit {
        it[Keys.widgetTheme] = theme.name
    }

    suspend fun setHighRefreshRateLists(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.highRefreshRateLists] = enabled
    }

    suspend fun setOpenPlayerOnTrackClick(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.openPlayerOnTrackClick] = enabled
    }

    private fun parseNavItems(raw: String?): List<String> =
        raw?.split(',')
            ?.let(::sanitizeNavItems)
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_BOTTOM_NAV_ITEMS

    private fun sanitizeNavItems(items: List<String>): List<String> {
        val known = BOTTOM_NAV_OPTIONS.map { it.id }.toSet()
        return items.map { it.trim() }.filter { it in known }.distinct()
    }
}
