package com.banlimusic.app.core.datastore

import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WidgetTheme { SYSTEM, BLACK, WHITE }

/** Side used by the tablet navigation rail; ignored on phones. */
enum class TabletNavPosition { LEFT, RIGHT }

/** Vertical position of the tablet mini player relative to page content. */
enum class TabletMiniPlayerPosition { TOP, BOTTOM }

const val DEFAULT_CACHE_MAX_BYTES = 200L * 1024L * 1024L
const val MIN_CACHE_MAX_BYTES = 50L * 1024L * 1024L
const val MAX_CACHE_MAX_BYTES = 2L * 1024L * 1024L * 1024L
const val DEFAULT_FAVORITE_CATEGORY = "默认收藏"
const val MAX_API_LIMIT_PER_WINDOW = 50
const val MIN_GD_REQUEST_INTERVAL_MS = 1_000L
const val MIN_SYNC_INTERVAL_HOURS = 1
const val MAX_SYNC_INTERVAL_HOURS = 168

/** User-facing cloud sync preferences; transient state lives in SyncStateStore. */
data class SyncSettings(
    val autoSync: Boolean = false,
    val intervalHours: Int = 6,
)

/** Defaults for a single GD source backend. */
@Serializable
data class GdProviderSettings(
    val enabledSourceKeys: Set<String> = setOf("netease"),
    val playbackQualityKbps: Int = 128,
    val apiLimitPerWindow: Int = 45,
    val searchPageSize: Int = 20,
)

/** Placeholder model for user-defined API sources. Runtime requests are not implemented yet. */
@Serializable
data class CustomSourceSettings(
    val id: String = "",
    val displayName: String = "",
    /** Secondary source name/code, e.g. the `source` query value of an API. */
    val providerCode: String = "",
    /** How a track id is represented, e.g. "{id}" or "{source}:{id}". */
    val trackIdFormat: String = "",
    val qualities: List<QualityOption> = emptyList(),
    val enabled: Boolean = false,
    val searchEndpoint: ApiEndpointSettings = ApiEndpointSettings(),
    val streamEndpoint: ApiEndpointSettings = ApiEndpointSettings(),
    val lyricsEndpoint: ApiEndpointSettings? = null,
    val artworkEndpoint: ApiEndpointSettings? = null,
)

@Serializable
data class QualityOption(
    val code: String = "",
    val kbps: Int = 0,
    val label: String = "",
)

/** Reserved description of a request/response mapping for custom sources. */
@Serializable
data class ApiEndpointSettings(
    val urlTemplate: String = "",
    val method: String = "GET",
    val queryParams: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    /** JSON path to a track object list, e.g. `data.songs`. */
    val resultListPath: String = "",
    /** JSON path to the track id inside a result item. */
    val trackIdPath: String = "",
    /** JSON path to the playable URL / lyric text / artwork URL. */
    val urlPath: String = "",
)

@Serializable
data class ProviderSettings(
    val gd: GdProviderSettings = GdProviderSettings(),
    val customSources: List<CustomSourceSettings> = emptyList(),
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Custom ARGB seed for the theme; null keeps dynamic/system colors. */
    val customThemeColor: Int? = null,
    val providers: ProviderSettings = ProviderSettings(),
    val sync: SyncSettings = SyncSettings(),
    val allowMobileNetwork: Boolean = true,
    val playbackRetryCount: Int = 3,
    val consecutiveFailureLimit: Int = 3,
    /**
     * When playback of the current source fails, try the other sources of the
     * same track (rotating after each retry) instead of stopping.
     */
    val autoSwitchPlaybackSource: Boolean = true,
    val defaultShowLyrics: Boolean = false,
    val downloadDirectory: String = "",
    /** Directory scanned for the desktop local-music library. */
    val localMusicDirectory: String = "",
    val downloadQualityKbps: Int = 128,
    val downloadLyrics: Boolean = false,
    val downloadArtwork: Boolean = false,
    /**
     * Allows track-list pages to request artwork from providers on demand.
     * When disabled, lists only read artwork that is already cached; the
     * player page always resolves artwork normally.
     */
    val requestListArtwork: Boolean = false,
    /**
     * Resolves artwork for the current track while it plays so the system media
     * controls and the desktop widget can show the poster even when the player
     * screen has never been opened. Cached artwork is always used.
     */
    val autoLoadArtwork: Boolean = true,
    val cacheMaxBytes: Long = DEFAULT_CACHE_MAX_BYTES,
    val bottomNavItems: List<String> = DEFAULT_BOTTOM_NAV_ITEMS,
    /** Side used by the tablet navigation rail; the phone bottom bar ignores it. */
    val tabletNavPosition: TabletNavPosition = TabletNavPosition.LEFT,
    /** Tablet mini player position; defaults to the bottom edge. */
    val tabletMiniPlayerPosition: TabletMiniPlayerPosition = TabletMiniPlayerPosition.BOTTOM,
    val agreementAccepted: Boolean = false,
    val defaultFavoriteCategory: String = DEFAULT_FAVORITE_CATEGORY,
    /** User defined order of favourite folders; unknown folders fall back to name order. */
    val favoriteCategoryOrder: List<String> = emptyList(),
    val autoImportLocalMusic: Boolean = false,
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Desktop-only placeholder for the floating lyrics window. */
    val desktopFloatingLyrics: Boolean = false,
    /** Requests a ~90Hz display mode while browsing track lists. */
    val highRefreshRateLists: Boolean = true,
    /** Opens the full player when a track row is tapped; disable to stay on the list. */
    val openPlayerOnTrackClick: Boolean = true,
    /** Tab opened on a cold start. All navigation items always stay visible. */
    val defaultNavItem: String = DEFAULT_NAV_ITEM,
    /** Last top-level page the user left the app from; empty means use the default. */
    val lastNavItem: String = "",
) {
    val qualityKbps: Int get() = providers.gd.playbackQualityKbps
    val enabledSources: Set<String> get() = providers.gd.enabledSourceKeys
    val apiLimitPerWindow: Int get() = providers.gd.apiLimitPerWindow
    val searchPageSize: Int get() = providers.gd.searchPageSize
}

data class SourceOption(val id: String, val label: String)

val SUPPORTED_SOURCES = listOf(
    SourceOption("netease", "GD网易云"),
    SourceOption("joox", "GD JOOX"),
    SourceOption("bilibili", "GD B站"),
)

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

const val DEFAULT_NAV_ITEM = "home"

/**
 * All known navigation items must stay visible. Unknown ids are dropped and
 * missing items are appended in their default relative order.
 */
fun sanitizeNavItems(items: List<String>): List<String> {
    val known = BOTTOM_NAV_OPTIONS.map { it.id }.toSet()
    val present = items.map { it.trim() }.filter { it in known }.distinct()
    return present + DEFAULT_BOTTOM_NAV_ITEMS.filter { it !in present }
}

fun sanitizeDefaultNavItem(item: String?, navItems: List<String>): String {
    val sanitizedItems = sanitizeNavItems(navItems)
    return item?.takeIf { it in sanitizedItems } ?: DEFAULT_NAV_ITEM
}

/**
 * Cold-start page: prefer the page the user left from, then the configured
 * default, then home. Only known top-level routes are accepted.
 */
fun resolveNavStart(
    lastVisited: String?,
    defaultItem: String?,
    validItems: Set<String>,
): String = lastVisited?.takeIf { it in validItems }
    ?: defaultItem?.takeIf { it in validItems }
    ?: DEFAULT_NAV_ITEM

/**
 * Applies the persisted folder order and appends folders that were never
 * reordered, so a newly created folder always shows at the right end.
 */
fun orderFavoriteCategories(all: List<String>, storedOrder: List<String>): List<String> {
    val available = all.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (available.isEmpty()) return emptyList()
    val known = storedOrder.map { it.trim() }.filter { it in available }.distinct()
    return known + available.filter { it !in known }.sorted()
}
