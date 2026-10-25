/**
 * 文件作用：通过 DataStore 读写应用设置，并处理校验与旧键兼容。
 */
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
import com.banlimusic.app.core.model.QualityTiers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val customThemeColor = longPreferencesKey("custom_theme_color")
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
        val requestListArtwork = booleanPreferencesKey("request_list_artwork")
        val autoLoadArtwork = booleanPreferencesKey("auto_load_artwork")
        val cacheMaxBytes = longPreferencesKey("cache_max_bytes")
        val bottomNavItems = stringPreferencesKey("bottom_nav_items")
        val agreementAccepted = booleanPreferencesKey("agreement_accepted")
        val defaultFavoriteCategory = stringPreferencesKey("default_favorite_category")
        val autoImportLocalMusic = booleanPreferencesKey("auto_import_local_music")
        val widgetTheme = stringPreferencesKey("widget_theme")
        val highRefreshRateLists = booleanPreferencesKey("high_refresh_rate_lists")
        val openPlayerOnTrackClick = booleanPreferencesKey("open_player_on_track_click")

        // New modular provider keys; the legacy keys above remain readable for upgrades.
        val gdEnabledSources = stringSetPreferencesKey("provider_gd_enabled_sources")
        val gdPlaybackQuality = intPreferencesKey("provider_gd_quality_kbps")
        val gdApiLimit = intPreferencesKey("provider_gd_api_limit")
        val gdSearchPageSize = intPreferencesKey("provider_gd_search_page_size")
        val customSources = stringPreferencesKey("provider_custom_sources_json")
        val defaultNavItem = stringPreferencesKey("default_nav_item")

        // Cloud sync preferences; transient sync state lives in SyncStateStore.
        val syncAuto = booleanPreferencesKey("sync_auto_enabled")
        val syncIntervalHours = intPreferencesKey("sync_interval_hours")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            val sources = (preferences[Keys.gdEnabledSources]
                ?: preferences[Keys.sources]
                ?: setOf("netease"))
                .filter { id -> SUPPORTED_SOURCES.any { it.id == id } }
                .ifEmpty { setOf("netease") }
                .toSet()
            val gd = GdProviderSettings(
                enabledSourceKeys = sources,
                playbackQualityKbps = QualityTiers.normalize(
                    (preferences[Keys.gdPlaybackQuality]
                        ?: preferences[Keys.quality]
                        ?: 128).takeIf { it > 0 } ?: 128,
                ),
                apiLimitPerWindow = (preferences[Keys.gdApiLimit]
                    ?: preferences[Keys.apiLimitPerWindow]
                    ?: 45).coerceIn(1, MAX_API_LIMIT_PER_WINDOW),
                searchPageSize = (preferences[Keys.gdSearchPageSize]
                    ?: preferences[Keys.searchPageSize]
                    ?: 20).coerceIn(10, 50),
            )
            val navItems = parseNavItems(preferences[Keys.bottomNavItems])
            AppSettings(
                themeMode = preferences[Keys.themeMode]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = preferences[Keys.dynamicColor] ?: true,
                customThemeColor = preferences[Keys.customThemeColor]
                    ?.takeIf { it != 0L }
                    ?.toInt(),
                providers = ProviderSettings(
                    gd = gd,
                    customSources = parseCustomSources(preferences[Keys.customSources]),
                ),
                sync = SyncSettings(
                    autoSync = preferences[Keys.syncAuto] ?: false,
                    intervalHours = (preferences[Keys.syncIntervalHours] ?: 6)
                        .coerceIn(MIN_SYNC_INTERVAL_HOURS, MAX_SYNC_INTERVAL_HOURS),
                ),
                allowMobileNetwork = preferences[Keys.allowMobileNetwork] ?: true,
                playbackRetryCount = (preferences[Keys.playbackRetryCount] ?: 3).coerceIn(0, 10),
                consecutiveFailureLimit = (preferences[Keys.consecutiveFailureLimit] ?: 3)
                    .coerceIn(1, 10),
                defaultShowLyrics = preferences[Keys.defaultShowLyrics] ?: false,
                downloadDirectory = preferences[Keys.downloadDirectory].orEmpty(),
                downloadQualityKbps = QualityTiers.normalize(
                    (preferences[Keys.downloadQuality] ?: 128).takeIf { it > 0 } ?: 128,
                ),
                downloadLyrics = preferences[Keys.downloadLyrics] ?: false,
                downloadArtwork = preferences[Keys.downloadArtwork] ?: false,
                requestListArtwork = preferences[Keys.requestListArtwork] ?: false,
                autoLoadArtwork = preferences[Keys.autoLoadArtwork] ?: true,
                cacheMaxBytes = (preferences[Keys.cacheMaxBytes] ?: DEFAULT_CACHE_MAX_BYTES)
                    .coerceIn(MIN_CACHE_MAX_BYTES, MAX_CACHE_MAX_BYTES),
                bottomNavItems = navItems,
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
                defaultNavItem = sanitizeDefaultNavItem(preferences[Keys.defaultNavItem], navItems),
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.settingsDataStore.edit {
        it[Keys.themeMode] = mode.name
    }

    suspend fun setDynamicColor(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.dynamicColor] = enabled
    }

    suspend fun setCustomThemeColor(color: Int?) = context.settingsDataStore.edit { preferences ->
        if (color == null) {
            preferences.remove(Keys.customThemeColor)
        } else {
            preferences[Keys.customThemeColor] = color.toLong() and 0xFFFFFFFFL
        }
    }

    suspend fun setQuality(qualityKbps: Int) = context.settingsDataStore.edit {
        it[Keys.gdPlaybackQuality] = QualityTiers.normalize(
            if (qualityKbps > 0) qualityKbps else 128,
        )
    }

    suspend fun setSourceEnabled(source: String, enabled: Boolean) =
        context.settingsDataStore.edit { preferences ->
            val current = preferences[Keys.gdEnabledSources]
                ?: preferences[Keys.sources]
                ?: setOf("netease")
            val updated = if (enabled) current + source else current - source
            preferences[Keys.gdEnabledSources] = updated
                .filter { id -> SUPPORTED_SOURCES.any { it.id == id } }
                .ifEmpty { setOf("netease") }
                .toSet()
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
        it[Keys.gdSearchPageSize] = size.coerceIn(10, 50)
    }

    suspend fun setApiLimitPerWindow(limit: Int) = context.settingsDataStore.edit {
        it[Keys.gdApiLimit] = limit.coerceIn(1, MAX_API_LIMIT_PER_WINDOW)
    }

    suspend fun setDownloadDirectory(path: String) = context.settingsDataStore.edit {
        it[Keys.downloadDirectory] = path.trim()
    }

    suspend fun setDownloadQuality(qualityKbps: Int) = context.settingsDataStore.edit {
        it[Keys.downloadQuality] = if (qualityKbps > 0) {
            QualityTiers.normalize(qualityKbps)
        } else {
            0
        }
    }

    suspend fun setDownloadLyrics(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.downloadLyrics] = enabled
    }

    suspend fun setDownloadArtwork(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.downloadArtwork] = enabled
    }

    suspend fun setRequestListArtwork(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.requestListArtwork] = enabled
    }

    suspend fun setAutoLoadArtwork(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.autoLoadArtwork] = enabled
    }

    suspend fun setCacheMaxBytes(bytes: Long) = context.settingsDataStore.edit {
        it[Keys.cacheMaxBytes] = bytes.coerceIn(MIN_CACHE_MAX_BYTES, MAX_CACHE_MAX_BYTES)
    }

    suspend fun setBottomNavItems(items: List<String>) = context.settingsDataStore.edit {
        it[Keys.bottomNavItems] = sanitizeNavItems(items).joinToString(",")
    }

    suspend fun setDefaultNavItem(item: String) = context.settingsDataStore.edit { preferences ->
        val navItems = parseNavItems(preferences[Keys.bottomNavItems])
        preferences[Keys.defaultNavItem] = sanitizeDefaultNavItem(item, navItems)
    }

    suspend fun setAgreementAccepted(accepted: Boolean) = context.settingsDataStore.edit {
        it[Keys.agreementAccepted] = accepted
    }

    suspend fun setDefaultFavoriteCategory(category: String) = context.settingsDataStore.edit {
        it[Keys.defaultFavoriteCategory] = category.trim().ifBlank { DEFAULT_FAVORITE_CATEGORY }
    }

    suspend fun setSyncAutoSync(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.syncAuto] = enabled
    }

    suspend fun setSyncIntervalHours(hours: Int) = context.settingsDataStore.edit {
        it[Keys.syncIntervalHours] = hours.coerceIn(MIN_SYNC_INTERVAL_HOURS, MAX_SYNC_INTERVAL_HOURS)
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

    suspend fun setCustomSources(sources: List<CustomSourceSettings>) = context.settingsDataStore.edit {
        it[Keys.customSources] = json.encodeToString(sources)
    }

    private fun parseCustomSources(raw: String?): List<CustomSourceSettings> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CustomSourceSettings>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun parseNavItems(raw: String?): List<String> {
        val parsed = raw?.split(',')?.let(::sanitizeNavItems)
        return parsed?.takeIf { it.isNotEmpty() } ?: sanitizeNavItems(emptyList())
    }
}
