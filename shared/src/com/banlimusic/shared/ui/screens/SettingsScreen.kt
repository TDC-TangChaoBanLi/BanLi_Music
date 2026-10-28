/**
 * 文件作用：两端共用的设置页，包含首页分类、全部二级页面、缓存歌曲与云同步状态。
 */
package com.banlimusic.shared.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.cache.CacheUsage
import com.banlimusic.app.core.cache.CachedTrack
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.BOTTOM_NAV_OPTIONS
import com.banlimusic.app.core.datastore.MAX_CACHE_MAX_BYTES
import com.banlimusic.app.core.datastore.MIN_CACHE_MAX_BYTES
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.datastore.TabletMiniPlayerPosition
import com.banlimusic.app.core.datastore.TabletNavPosition
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.datastore.sanitizeNavItems
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.shared.resources.Res
import com.banlimusic.shared.resources.banli_logo
import com.banlimusic.shared.ui.agreement.AgreementContent
import com.banlimusic.shared.ui.components.BanLiDetailHeader
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiSettingsActionRow
import com.banlimusic.shared.ui.components.BanLiSettingsCard
import com.banlimusic.shared.ui.components.BanLiSettingsNavRow
import com.banlimusic.shared.ui.components.BanLiSettingsSectionLabel
import com.banlimusic.shared.ui.components.BanLiSettingsSlider
import com.banlimusic.shared.ui.components.BanLiSettingsSwitchRow
import com.banlimusic.shared.ui.components.BanLiSettingsValueRow
import com.banlimusic.shared.ui.components.BanLiTrackListRow
import com.banlimusic.shared.ui.components.CompactTextButton
import com.banlimusic.shared.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.shared.ui.components.TrackListHeaderSpec
import com.banlimusic.shared.ui.components.TrackListPanel
import com.banlimusic.shared.ui.components.formatBytes
import com.banlimusic.shared.ui.components.qualityShortLabel
import com.banlimusic.shared.ui.theme.BANLI_DEFAULT_SEED
import org.jetbrains.compose.resources.painterResource

private const val MIB = 1024L * 1024L

private val CUSTOM_THEME_SWATCHES = listOf(
    0xFF0F766E.toInt(),
    0xFF2563EB.toInt(),
    0xFF7C3AED.toInt(),
    0xFFDB2777.toInt(),
    0xFFDC2626.toInt(),
    0xFFEA580C.toInt(),
    0xFFCA8A04.toInt(),
)

/** Platform-specific switches inside the shared settings page. */
enum class SettingsPlatform { ANDROID, DESKTOP }

/** Cloud sync snapshot without importing Android-specific classes. */
enum class SyncAuthState { NOT_CONFIGURED, SIGNED_OUT, SIGNED_IN }

data class SyncUiState(
    val supported: Boolean = true,
    val configured: Boolean = false,
    val auth: SyncAuthState = SyncAuthState.NOT_CONFIGURED,
    val accountName: String? = null,
    val signatureSha1: String = "",
    val signatureHash: String = "",
    val redirectUri: String = "",
    val autoSync: Boolean = false,
    val intervalHours: Int = 6,
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
    val successAdded: Int = 0,
    val successRemoved: Int = 0,
    val successText: String? = null,
    val unavailableReason: String? = null,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val versionName: String = "未知版本",
    val rateStatus: RateLimitStatus? = null,
    val cacheUsage: CacheUsage = CacheUsage(),
    val cachedTracks: List<CachedTrack> = emptyList(),
    val sync: SyncUiState = SyncUiState(),
    val playbackQualities: List<Int> = QualityTiers.PRESET_KBPS,
    val downloadQualities: List<Int> = QualityTiers.PRESET_KBPS,
    val widgetSupported: Boolean = true,
    val audioCacheSupported: Boolean = true,
    /** Desktop offers a native directory picker; Android edits the path directly. */
    val supportsDirectoryPicker: Boolean = false,
    val platform: SettingsPlatform = SettingsPlatform.ANDROID,
)

interface SettingsActions {
    fun setThemeMode(mode: ThemeMode) {}
    fun setDynamicColor(enabled: Boolean) {}
    fun setCustomThemeColor(color: Int?) {}
    fun setHighRefreshRateLists(enabled: Boolean) {}
    fun setBottomNavItems(items: List<String>) {}
    fun setDefaultNavItem(item: String) {}
    fun setTabletNavPosition(position: TabletNavPosition) {}
    fun setTabletMiniPlayerPosition(position: TabletMiniPlayerPosition) {}
    fun setWidgetTheme(theme: WidgetTheme) {}
    fun setDesktopFloatingLyrics(enabled: Boolean) {}

    fun setAllowMobileNetwork(enabled: Boolean) {}
    fun setDefaultShowLyrics(enabled: Boolean) {}
    fun setOpenPlayerOnTrackClick(enabled: Boolean) {}
    fun setAutoSwitchPlaybackSource(enabled: Boolean) {}
    fun setPlaybackRetryCount(count: Int) {}
    fun setConsecutiveFailureLimit(limit: Int) {}
    fun setQuality(kbps: Int) {}

    fun setSourceEnabled(source: String, enabled: Boolean) {}
    fun setApiLimit(limit: Int) {}
    fun setSearchPageSize(size: Int) {}
    fun refreshRateStatus() {}

    fun setDownloadDirectory(path: String) {}
    fun chooseDownloadDirectory() {}
    fun setDownloadQuality(kbps: Int) {}
    fun setDownloadLyrics(enabled: Boolean) {}
    fun setDownloadArtwork(enabled: Boolean) {}
    fun setRequestListArtwork(enabled: Boolean) {}
    fun setAutoLoadArtwork(enabled: Boolean) {}
    fun setCacheMaxBytes(bytes: Long) {}
    fun clearAudioCache() {}
    fun clearArtworkCache() {}
    fun clearLyricsCache() {}
    fun clearOtherCache() {}
    fun clearAllCaches() {}
    fun refreshCacheUsage() {}
    fun removeCachedTrack(cacheKey: String) {}
    fun convertCachedToDownloads(entries: List<CachedTrack>) {}

    fun setDefaultFavoriteCategory(category: String) {}
    fun setAutoImportLocalMusic(enabled: Boolean) {}
    fun exportFavorites() {}
    fun importFavorites(replace: Boolean) {}
    fun clearSearchHistory() {}

    fun signInSync() {}
    fun signOutSync(deleteCloudFile: Boolean) {}
    fun syncNow() {}
    fun setAutoSync(enabled: Boolean) {}
    fun setSyncIntervalHours(hours: Int) {}

    fun copyText(label: String, value: String) {}
}

/** Back navigation for the settings page stack. */
class SettingsScreenController {
    internal var backHandler: (() -> Boolean)? = null
    fun handleBack(): Boolean = backHandler?.invoke() == true
}

private enum class SettingsPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    APPEARANCE("外观", "主题、导航栏与窗口布局", Icons.Rounded.Palette),
    PLAYBACK("播放", "失败重试、歌词默认页与网络策略", Icons.Rounded.PlayCircle),
    SOURCE("播放源", "GD 音乐台与预留的自定义 API 来源", Icons.Rounded.CloudQueue),
    DOWNLOAD_CACHE("下载与缓存", "下载目录、缓存上限与缓存歌曲", Icons.Rounded.Download),
    LIBRARY("音乐库与收藏", "默认收藏夹与本地音乐", Icons.Rounded.LibraryMusic),
    SYNC("云同步", "使用自己的 OneDrive 同步收藏夹", Icons.Rounded.CloudQueue),
    ABOUT("关于", "版本与来源声明", Icons.Rounded.Info),
    CACHED_TRACKS("缓存歌曲", "查看缓存歌曲并转换为下载", Icons.Rounded.QueueMusic),
}

private val HOME_PAGES = SettingsPage.entries.filter { it != SettingsPage.CACHED_TRACKS }

private enum class CacheFilter(val label: String) {
    ALL("全部"),
    COMPLETE("完整缓存"),
    PARTIAL("部分缓存"),
}

private val CACHED_SORT_OPTIONS = listOf(TrackSort.TITLE, TrackSort.ARTIST, TrackSort.CREATED)

private fun navLabel(id: String): String =
    BOTTOM_NAV_OPTIONS.firstOrNull { it.id == id }?.label ?: id

private fun navIcon(id: String): ImageVector = when (id) {
    "home" -> Icons.Rounded.Home
    "search" -> Icons.Rounded.Search
    "library" -> Icons.Rounded.LibraryMusic
    "downloads" -> Icons.Rounded.Download
    "settings" -> Icons.Rounded.Settings
    else -> Icons.Rounded.ViewList
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun widgetThemeLabel(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.SYSTEM -> "跟随系统"
    WidgetTheme.BLACK -> "深色"
    WidgetTheme.WHITE -> "浅色"
}

private fun rateStatusText(status: RateLimitStatus?): String = when {
    status == null -> "限流状态未知"
    status.remaining <= 0 -> "请求额度已用完，请稍后再试"
    else -> "5 分钟内剩余 ${status.remaining} 次请求"
}

private fun formatSyncTime(value: Long): String =
    if (value <= 0L) {
        "尚未同步"
    } else {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(value))
    }

@Composable
fun BanLiSettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    controller: SettingsScreenController = remember { SettingsScreenController() },
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    DisposableEffect(controller, page) {
        controller.backHandler = {
            when (page) {
                null -> false
                SettingsPage.CACHED_TRACKS -> {
                    page = SettingsPage.DOWNLOAD_CACHE
                    true
                }
                else -> {
                    page = null
                    true
                }
            }
        }
        onDispose { controller.backHandler = null }
    }

    if (page == null) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(text = "设置", style = MaterialTheme.typography.headlineSmall)
            }
            items(HOME_PAGES) { entry ->
                BanLiSettingsNavRow(
                    title = entry.title,
                    description = entry.description,
                    onClick = { page = entry },
                    icon = entry.icon,
                )
            }
        }
        return
    }

    val current = page ?: return
    Column(modifier = modifier.fillMaxSize()) {
        BanLiDetailHeader(
            title = current.title,
            onBack = {
                page = if (current == SettingsPage.CACHED_TRACKS) {
                    SettingsPage.DOWNLOAD_CACHE
                } else {
                    null
                }
            },
            icon = current.icon,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
        )
        if (current == SettingsPage.CACHED_TRACKS) {
            CachedTracksPage(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    when (current) {
                        SettingsPage.APPEARANCE -> AppearanceSettings(state, actions)
                        SettingsPage.PLAYBACK -> PlaybackSettings(state, actions)
                        SettingsPage.SOURCE -> SourceSettings(state, actions)
                        SettingsPage.DOWNLOAD_CACHE -> DownloadCacheSettings(
                            state = state,
                            actions = actions,
                            onOpenCachedTracks = { page = SettingsPage.CACHED_TRACKS },
                        )
                        SettingsPage.LIBRARY -> LibrarySettings(state, actions)
                        SettingsPage.SYNC -> SyncSettingsPage(state, actions)
                        SettingsPage.ABOUT -> AboutSettings(state, actions)
                        SettingsPage.CACHED_TRACKS -> Unit
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    var navItems by remember(settings.bottomNavItems) {
        mutableStateOf(sanitizeNavItems(settings.bottomNavItems))
    }

    fun commit(updated: List<String>) {
        navItems = updated
        actions.setBottomNavItems(updated)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BanLiSettingsCard(title = "主题与外观", leading = { SettingsIcon(Icons.Rounded.Palette) }) {
            Text("主题模式", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { actions.setThemeMode(mode) },
                        label = { Text(themeLabel(mode)) },
                    )
                }
            }
            if (state.platform == SettingsPlatform.DESKTOP) {
                BanLiSettingsSwitchRow(
                    icon = Icons.Rounded.Palette,
                    title = "默认主题",
                    description = "使用应用内置的暖色主题；关闭后可选择自定义主题色",
                    checked = settings.customThemeColor == null,
                    onCheckedChange = { enabled ->
                        actions.setCustomThemeColor(if (enabled) null else BANLI_DEFAULT_SEED)
                    },
                )
            } else {
                BanLiSettingsSwitchRow(
                    icon = Icons.Rounded.ColorLens,
                    title = "动态取色",
                    description = "Android 12+ 使用系统壁纸颜色；关闭后可自定义主题颜色",
                    checked = settings.dynamicColor,
                    onCheckedChange = actions::setDynamicColor,
                )
                BanLiSettingsSwitchRow(
                    icon = Icons.Rounded.Speed,
                    title = "列表高刷新率",
                    description = "浏览列表时尝试使用 90Hz 显示模式",
                    checked = settings.highRefreshRateLists,
                    onCheckedChange = actions::setHighRefreshRateLists,
                )
            }
            HorizontalDivider()
            if (state.platform != SettingsPlatform.DESKTOP && settings.dynamicColor) {
                BanLiSettingsSectionLabel(Icons.Rounded.ColorLens, "自定义主题颜色")
                Text(
                    text = "关闭“动态取色”后才能自定义主题颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CustomThemeColorSection(
                    current = settings.customThemeColor,
                    onPick = actions::setCustomThemeColor,
                )
            }
        }

        BanLiSettingsCard(title = "导航栏", leading = { SettingsIcon(Icons.Rounded.ViewList) }) {
            Text(
                "手机底部导航、平板侧边导航与桌面导航共用这些栏目，可调整顺序；默认栏目在下次启动时生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            navItems.forEachIndexed { index, id ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(22.dp),
                    )
                    Icon(navIcon(id), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = navLabel(id),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        enabled = index > 0,
                        onClick = {
                            val updated = navItems.toMutableList()
                            updated.add(index - 1, updated.removeAt(index))
                            commit(updated)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) { Text("上移") }
                    TextButton(
                        enabled = index < navItems.lastIndex,
                        onClick = {
                            val updated = navItems.toMutableList()
                            updated.add(index + 1, updated.removeAt(index))
                            commit(updated)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) { Text("下移") }
                }
            }
            HorizontalDivider()
            Text("默认栏目", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                navItems.forEach { id ->
                    FilterChip(
                        selected = settings.defaultNavItem == id,
                        onClick = { actions.setDefaultNavItem(id) },
                        label = { Text(navLabel(id)) },
                    )
                }
            }
        }

        BanLiSettingsCard(title = "平板与桌面布局", leading = { SettingsIcon(Icons.Rounded.SwapHoriz) }) {
            Text(
                "以下选项在平板（最小宽度 ≥ 600dp）与桌面端生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("侧边导航栏位置", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabletNavPosition.entries.forEach { position ->
                    FilterChip(
                        selected = settings.tabletNavPosition == position,
                        onClick = { actions.setTabletNavPosition(position) },
                        label = {
                            Text(if (position == TabletNavPosition.LEFT) "左侧" else "右侧")
                        },
                    )
                }
            }
            HorizontalDivider()
            Text("正在播放控件位置", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabletMiniPlayerPosition.entries.forEach { position ->
                    FilterChip(
                        selected = settings.tabletMiniPlayerPosition == position,
                        onClick = { actions.setTabletMiniPlayerPosition(position) },
                        label = {
                            Text(
                                if (position == TabletMiniPlayerPosition.TOP) "上方" else "下方",
                            )
                        },
                    )
                }
            }
            Text(
                "平板横屏与桌面播放页固定为左侧海报与播放控件、右侧歌词。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.platform == SettingsPlatform.DESKTOP) {
            BanLiSettingsCard(
                title = "桌面悬浮歌词",
                leading = { SettingsIcon(Icons.Rounded.Lyrics) },
            ) {
                BanLiSettingsSwitchRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "启用桌面悬浮歌词",
                    description = "功能开发中；当前仅保存开关状态，不会创建悬浮窗口",
                    checked = settings.desktopFloatingLyrics,
                    onCheckedChange = actions::setDesktopFloatingLyrics,
                )
                Text(
                    text = "后续版本将支持在桌面显示当前歌词，并提供置顶、透明度和锁定选项。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            BanLiSettingsCard(title = "桌面小组件", leading = { SettingsIcon(Icons.Rounded.Widgets) }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.widgetTheme == theme,
                            onClick = { actions.setWidgetTheme(theme) },
                            label = { Text(widgetThemeLabel(theme)) },
                        )
                    }
                }
                Text(
                    if (state.widgetSupported) {
                        "3×1 显示歌名、歌手、播放控件与底部进度条；4×1 额外显示封面与收藏按钮。"
                    } else {
                        "当前平台不提供系统桌面小组件。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomThemeColorSection(
    current: Int?,
    onPick: (Int?) -> Unit,
) {
    val fallback = MaterialTheme.colorScheme.primary.toArgb()
    val seedHsl = remember(current, fallback) { hslOf(Color(current ?: fallback)) }
    var hue by remember(current, fallback) { mutableFloatStateOf(seedHsl[0]) }
    var saturation by remember(current, fallback) { mutableFloatStateOf(seedHsl[1]) }
    val preview = Color.hsl(hue, saturation.coerceIn(0.15f, 1f), 0.62f)
    val customSelected = current != null && current !in CUSTOM_THEME_SWATCHES

    BanLiSettingsSectionLabel(Icons.Rounded.ColorLens, "自定义主题颜色")
    Text(
        text = "从 7 个预设颜色或一个自定义颜色中选择主题色。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CUSTOM_THEME_SWATCHES.forEach { argb ->
            val selected = current == argb
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onPick(argb) }
                    .semantics {
                        contentDescription = String.format(
                            java.util.Locale.ROOT,
                            "主题颜色 #%06X",
                            argb and 0xFFFFFF,
                        )
                    },
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFFE53935),
                            Color(0xFFFDD835),
                            Color(0xFF43A047),
                            Color(0xFF00ACC1),
                            Color(0xFF1E88E5),
                            Color(0xFF8E24AA),
                            Color(0xFFE53935),
                        ),
                    ),
                )
                .border(
                    width = if (customSelected) 3.dp else 1.dp,
                    color = if (customSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .clickable { onPick(preview.toArgb()) }
                .semantics { contentDescription = "自定义主题颜色" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ColorLens,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
    if (customSelected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(preview),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "自定义颜色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("色相", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            valueRange = 0f..360f,
            onValueChangeFinished = { onPick(preview.toArgb()) },
        )
        Text("饱和度", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            valueRange = 0.15f..1f,
            onValueChangeFinished = { onPick(preview.toArgb()) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSettings(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings

    BanLiSettingsCard(title = "播放行为", leading = { SettingsIcon(Icons.Rounded.PlayCircle) }) {
        if (state.platform == SettingsPlatform.ANDROID) {
            BanLiSettingsSwitchRow(
                icon = Icons.Rounded.Wifi,
                title = "允许移动网络播放",
                description = "关闭后仅在 Wi-Fi 等非计费网络下播放和下载",
                checked = settings.allowMobileNetwork,
                onCheckedChange = actions::setAllowMobileNetwork,
            )
        }
        BanLiSettingsSwitchRow(
            icon = Icons.Rounded.Lyrics,
            title = "单曲页默认显示歌词",
            description = "关闭时默认显示海报；平板横屏与桌面端始终同时显示歌词",
            checked = settings.defaultShowLyrics,
            onCheckedChange = actions::setDefaultShowLyrics,
        )
        BanLiSettingsSwitchRow(
            icon = Icons.Rounded.TouchApp,
            title = "点按歌曲进入播放页",
            description = "关闭后点按列表只播放，不自动打开全屏播放器",
            checked = settings.openPlayerOnTrackClick,
            onCheckedChange = actions::setOpenPlayerOnTrackClick,
        )
        BanLiSettingsSwitchRow(
            icon = Icons.Rounded.SwapHoriz,
            title = "播放失败自动换源",
            description = "当前来源播放失败时，按顺序尝试这首歌的其他来源",
            checked = settings.autoSwitchPlaybackSource,
            onCheckedChange = actions::setAutoSwitchPlaybackSource,
        )
        BanLiSettingsSlider(
            icon = Icons.Rounded.Replay,
            title = "失败自动重试次数",
            valueText = "${settings.playbackRetryCount} 次",
            value = settings.playbackRetryCount.toFloat(),
            valueRange = 0f..10f,
            steps = 9,
            onValueChangeFinished = { actions.setPlaybackRetryCount(it.toInt()) },
        )
        Text(
            "重试间隔依次为 2、4、8、16 秒。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BanLiSettingsSlider(
            icon = Icons.Rounded.ErrorOutline,
            title = "连续失败停止阈值",
            valueText = "${settings.consecutiveFailureLimit} 首",
            value = settings.consecutiveFailureLimit.toFloat(),
            valueRange = 1f..10f,
            steps = 8,
            onValueChangeFinished = { actions.setConsecutiveFailureLimit(it.toInt()) },
        )
        Text(
            "连续多首歌曲播放失败后会停止播放并提示。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        BanLiSettingsSectionLabel(Icons.Rounded.HighQuality, "播放音质")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.playbackQualities.forEach { quality ->
                FilterChip(
                    selected = settings.qualityKbps == quality,
                    onClick = { actions.setQuality(quality) },
                    label = { Text(qualityShortLabel(quality)) },
                )
            }
        }
        Text(
            "所有音乐源统一使用标准、较高、高品、无损四档；来源档位不足时映射到最接近的音质。" +
                "桌面播放器使用 JLayer 解码 MP3，无损档位仅 Android 端可用。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceSettings(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BanLiSettingsCard(title = "GD 音乐台", leading = { SettingsIcon(Icons.Rounded.CloudQueue) }) {
            Text(
                "GD 音乐台是当前内置播放源，多个二级音源共享同一后端限流额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SUPPORTED_SOURCES.forEach { source ->
                BanLiSettingsSwitchRow(
                    icon = Icons.Rounded.CloudQueue,
                    title = source.label,
                    description = "二级音源标识：${source.id}",
                    checked = source.id in settings.enabledSources,
                    onCheckedChange = { actions.setSourceEnabled(source.id, it) },
                )
            }
            HorizontalDivider()
            BanLiSettingsSlider(
                icon = Icons.Rounded.Speed,
                title = "5 分钟请求上限",
                valueText = "${settings.apiLimitPerWindow} 次",
                value = settings.apiLimitPerWindow.toFloat(),
                valueRange = 1f..50f,
                steps = 48,
                onValueChangeFinished = { actions.setApiLimit(it.toInt()) },
            )
            Text(
                "GD 音乐台强制每次请求间隔至少 1 秒，服务端上限为 50 次/5 分钟。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BanLiSettingsSectionLabel(Icons.Rounded.FormatListNumbered, "每次搜索/加载数量")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 50).forEach { size ->
                    FilterChip(
                        selected = settings.searchPageSize == size,
                        onClick = { actions.setSearchPageSize(size) },
                        label = { Text("$size 首") },
                    )
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rateStatusText(state.rateStatus),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = actions::refreshRateStatus) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新状态")
                }
            }
        }

        BanLiSettingsCard(
            title = "自定义 API 来源（预留）",
            leading = { SettingsIcon(Icons.Rounded.AddLink) },
        ) {
            Text(
                "该模块为后续接入自建音乐服务器预留，当前不会发起任何自定义请求。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                "二级音乐源名称与对应代码",
                "自定义曲目 ID 表示方式",
                "自定义音质代码及显示名称",
                "自定义搜索、获取音乐、歌词与海报 API 格式及参数映射",
            ).forEach { text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("添加自定义来源（暂未开放）")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadCacheSettings(
    state: SettingsUiState,
    actions: SettingsActions,
    onOpenCachedTracks: () -> Unit,
) {
    val settings = state.settings
    var directory by remember(settings.downloadDirectory) {
        mutableStateOf(settings.downloadDirectory)
    }
    var cacheMbText by remember(settings.cacheMaxBytes) {
        mutableStateOf((settings.cacheMaxBytes / MIB).toString())
    }
    var cacheMbValue by remember(settings.cacheMaxBytes) {
        mutableFloatStateOf(settings.cacheMaxBytes.toFloat() / MIB)
    }
    var pendingClear by remember { mutableStateOf<CacheClearTarget?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BanLiSettingsCard(title = "下载", leading = { SettingsIcon(Icons.Rounded.Download) }) {
            OutlinedTextField(
                value = directory,
                onValueChange = { directory = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("下载目录（留空使用系统音乐目录）") },
                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                singleLine = true,
            )
            Row {
                TextButton(onClick = { actions.setDownloadDirectory(directory) }) {
                    Text("保存路径")
                }
                if (state.supportsDirectoryPicker) {
                    TextButton(onClick = actions::chooseDownloadDirectory) {
                        Text("选择目录")
                    }
                }
            }
            BanLiSettingsSectionLabel(Icons.Rounded.HighQuality, "默认下载音质")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.downloadQualities.forEach { quality ->
                    FilterChip(
                        selected = settings.downloadQualityKbps == quality,
                        onClick = { actions.setDownloadQuality(quality) },
                        label = { Text(qualityShortLabel(quality)) },
                    )
                }
            }
            BanLiSettingsSwitchRow(
                icon = Icons.Rounded.Subtitles,
                title = "同时下载歌词",
                description = "保存为与歌曲同名的 .lrc 文件",
                checked = settings.downloadLyrics,
                onCheckedChange = actions::setDownloadLyrics,
            )
            BanLiSettingsSwitchRow(
                icon = Icons.Rounded.Image,
                title = "同时下载海报",
                description = "保存为与歌曲同名的 .jpg 文件",
                checked = settings.downloadArtwork,
                onCheckedChange = actions::setDownloadArtwork,
            )
        }

        BanLiSettingsCard(title = "缓存", leading = { SettingsIcon(Icons.Rounded.Storage) }) {
            BanLiSettingsSwitchRow(
                icon = Icons.Rounded.Image,
                title = "列表页请求海报",
                description = "关闭时列表只读取已缓存海报；播放页始终加载",
                checked = settings.requestListArtwork,
                onCheckedChange = actions::setRequestListArtwork,
            )
            BanLiSettingsSwitchRow(
                icon = Icons.Rounded.Image,
                title = "播放时自动加载海报",
                description = "当前歌曲没有海报缓存时自动请求",
                checked = settings.autoLoadArtwork,
                onCheckedChange = actions::setAutoLoadArtwork,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    BanLiSettingsSectionLabel(Icons.Rounded.Storage, "缓存上限")
                    Text(
                        formatBytes(state.cacheUsage.totalBytes) + " / " +
                            formatBytes(settings.cacheMaxBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = cacheMbText,
                    onValueChange = { cacheMbText = it.filter(Char::isDigit) },
                    modifier = Modifier.width(96.dp),
                    label = { Text("MB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = {
                        val mb = cacheMbText.toLongOrNull() ?: return@TextButton
                        val bytes = (mb * MIB).coerceIn(MIN_CACHE_MAX_BYTES, MAX_CACHE_MAX_BYTES)
                        cacheMbValue = bytes.toFloat() / MIB
                        cacheMbText = (bytes / MIB).toString()
                        actions.setCacheMaxBytes(bytes)
                    },
                ) { Text("应用") }
            }
            Slider(
                value = cacheMbValue.coerceIn(
                    MIN_CACHE_MAX_BYTES.toFloat() / MIB,
                    MAX_CACHE_MAX_BYTES.toFloat() / MIB,
                ),
                onValueChange = {
                    cacheMbValue = it
                    cacheMbText = it.toLong().toString()
                },
                valueRange = 50f..2048f,
                steps = 38,
                onValueChangeFinished = {
                    val bytes = (cacheMbValue.toLong() * MIB)
                        .coerceIn(MIN_CACHE_MAX_BYTES, MAX_CACHE_MAX_BYTES)
                    actions.setCacheMaxBytes(bytes)
                },
            )
            BanLiSettingsValueRow(
                icon = Icons.Rounded.MusicNote,
                title = "歌曲缓存",
                value = formatBytes(state.cacheUsage.audioBytes),
                onClear = { pendingClear = CacheClearTarget.AUDIO },
                enabled = state.audioCacheSupported,
            )
            BanLiSettingsValueRow(
                icon = Icons.Rounded.Image,
                title = "海报缓存",
                value = formatBytes(state.cacheUsage.artworkBytes),
                onClear = { pendingClear = CacheClearTarget.ARTWORK },
            )
            BanLiSettingsValueRow(
                icon = Icons.Rounded.Lyrics,
                title = "歌词缓存",
                value = formatBytes(state.cacheUsage.lyricsBytes),
                onClear = { pendingClear = CacheClearTarget.LYRICS },
            )
            BanLiSettingsValueRow(
                icon = Icons.Rounded.PieChart,
                title = "其他缓存",
                value = formatBytes(state.cacheUsage.otherBytes),
                onClear = { pendingClear = CacheClearTarget.OTHER },
            )
            BanLiSettingsActionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "缓存歌曲",
                description = "查看详细列表并转换为已下载歌曲",
                actionText = "查看",
                onClick = onOpenCachedTracks,
            )
            BanLiSettingsActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "清理全部缓存",
                description = "不会删除已下载文件；正在下载的临时文件会保留",
                actionText = "清理",
                onClick = { pendingClear = CacheClearTarget.ALL },
            )
            TextButton(onClick = actions::refreshCacheUsage) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("刷新缓存大小")
            }
        }
    }

    pendingClear?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(target.title) },
            text = { Text(target.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            CacheClearTarget.AUDIO -> actions.clearAudioCache()
                            CacheClearTarget.ARTWORK -> actions.clearArtworkCache()
                            CacheClearTarget.LYRICS -> actions.clearLyricsCache()
                            CacheClearTarget.OTHER -> actions.clearOtherCache()
                            CacheClearTarget.ALL -> actions.clearAllCaches()
                        }
                        pendingClear = null
                    },
                ) { Text("确认清理") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("取消") }
            },
        )
    }
}

private enum class CacheClearTarget(val title: String, val message: String) {
    AUDIO("清理歌曲缓存", "将删除已缓存的播放音频，已下载文件不受影响。"),
    ARTWORK("清理海报缓存", "将删除列表、系统媒体控件和桌面小组件使用的缓存海报。"),
    LYRICS("清理歌词缓存", "将删除已缓存的歌词文件。"),
    OTHER("清理其他缓存", "将删除 HTTP 请求、图片等临时缓存。"),
    ALL(
        "清理全部缓存",
        "将删除歌曲、海报、歌词和其他缓存；不会删除已下载文件，正在下载的临时文件会保留。",
    ),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySettings(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    var defaultCategory by remember(settings.defaultFavoriteCategory) {
        mutableStateOf(settings.defaultFavoriteCategory)
    }
    var showImportChoice by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BanLiSettingsCard(
            title = "音乐库与收藏",
            leading = { SettingsIcon(Icons.Rounded.LibraryMusic) },
        ) {
            OutlinedTextField(
                value = defaultCategory,
                onValueChange = { defaultCategory = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("默认收藏夹") },
                leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, contentDescription = null) },
                singleLine = true,
            )
            TextButton(onClick = { actions.setDefaultFavoriteCategory(defaultCategory) }) {
                Text("保存默认收藏夹")
            }
            BanLiSettingsSwitchRow(
                icon = Icons.Rounded.AudioFile,
                title = "启动时自动导入本地音乐",
                description = "需要媒体读取权限；只记录媒体库中的音频，不复制文件",
                checked = settings.autoImportLocalMusic,
                onCheckedChange = actions::setAutoImportLocalMusic,
            )
            Text(text = "收藏备份", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions::exportFavorites) {
                    Icon(Icons.Rounded.ImportExport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出收藏")
                }
                TextButton(onClick = { showImportChoice = true }) {
                    Text("导入收藏")
                }
            }
            Text(
                "导出文件包含收藏夹、每首歌所属的多个收藏夹、添加时间与曲目元数据。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            BanLiSettingsActionRow(
                icon = Icons.Rounded.Search,
                title = "清空搜索历史",
                description = "只清除搜索建议，不影响收藏和播放记录",
                actionText = "清空",
                onClick = actions::clearSearchHistory,
            )
        }
    }

    if (showImportChoice) {
        AlertDialog(
            onDismissRequest = { showImportChoice = false },
            title = { Text("导入收藏") },
            text = {
                Column {
                    Text(
                        "选择“覆盖导入”会移除当前收藏（并在下次同步时同步到云端）；" +
                            "选择“仅追加”会保留现有收藏。",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportChoice = false
                        actions.importFavorites(replace = false)
                    },
                ) { Text("仅追加") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showImportChoice = false
                            actions.importFavorites(replace = true)
                        },
                    ) { Text("覆盖导入") }
                    TextButton(onClick = { showImportChoice = false }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun CachedTracksPage(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(CacheFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(TrackSort.CREATED) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var pendingDeleteKeys by remember { mutableStateOf<Set<String>?>(null) }
    val listState = rememberLazyListState()
    val cache = state.cachedTracks

    val completeCount = cache.count { it.isComplete }
    val partialCount = cache.size - completeCount
    val filtered = remember(cache, filter) {
        when (filter) {
            CacheFilter.ALL -> cache
            CacheFilter.COMPLETE -> cache.filter { it.isComplete }
            CacheFilter.PARTIAL -> cache.filterNot { it.isComplete }
        }
    }
    val sorted = remember(filtered, sort, ascending) {
        val order = TrackSorter.sort(filtered.map { it.track }, sort, ascending)
            .map { it.id }
            .withIndex()
            .associate { it.value to it.index }
        filtered.sortedBy { order[it.track.id] ?: Int.MAX_VALUE }
    }
    val sections = remember(sorted, sort) {
        TrackSorter.sections(sorted.map { it.track }, sort).map {
            it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT)
        }
    }
    fun exitSelection() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    TrackListPanel(
        items = sorted,
        itemKey = { it.cacheKey },
        validKey = { it.cacheKey },
        spec = TrackListHeaderSpec(
            countText = "完整缓存 $completeCount 首 · 部分缓存 $partialCount 首",
            selectionMode = selectionMode,
            selectedCount = selectedKeys.size,
            sort = sort,
            sortOptions = CACHED_SORT_OPTIONS,
            ascending = ascending,
            categories = listOf(CacheFilter.COMPLETE.label, CacheFilter.PARTIAL.label),
            selectedCategory = if (filter == CacheFilter.ALL) null else filter.label,
            allCategoryLabel = "全部",
        ),
        listState = listState,
        sections = sections,
        ascending = ascending,
        selectionMode = selectionMode,
        onStartSelection = { selectionMode = true },
        onSelect = { key ->
            selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        },
        onSelectAll = { selectedKeys = sorted.map { it.cacheKey }.toSet() },
        onCancelSelection = ::exitSelection,
        onSort = { sort = it },
        onToggleOrder = { ascending = !ascending },
        onCategory = { category ->
            filter = when (category) {
                CacheFilter.COMPLETE.label -> CacheFilter.COMPLETE
                CacheFilter.PARTIAL.label -> CacheFilter.PARTIAL
                else -> CacheFilter.ALL
            }
            exitSelection()
        },
        modifier = modifier,
        immediateDragSelection = state.platform == SettingsPlatform.DESKTOP,
        countActions = {
            CompactTextButton(text = "刷新", onClick = actions::refreshCacheUsage)
            if (sorted.isNotEmpty()) {
                CompactTextButton(text = "选择", onClick = { selectionMode = true })
            }
        },
        emptyContent = {
            BanLiEmptyState(
                title = when {
                    !state.audioCacheSupported -> "桌面端不保留播放缓存"
                    filter == CacheFilter.ALL -> "还没有缓存歌曲"
                    else -> "没有${filter.label}的歌曲"
                },
                description = if (state.audioCacheSupported) {
                    "播放过的歌曲会出现在这里，可转换为已下载歌曲。"
                } else {
                    "桌面端直接流式播放，不保留音频缓存；下载完成的歌曲请在下载页查看。"
                },
                icon = Icons.Rounded.QueueMusic,
            )
        },
        footer = {
            Text(
                text = "缓存歌曲来自播放时自动保存的音频；转为下载后会保存到下载目录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        },
        selectionBar = {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已选 ${selectedKeys.size} 首缓存",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = selectedKeys.isNotEmpty(),
                            onClick = {
                                actions.convertCachedToDownloads(
                                    sorted.filter { it.cacheKey in selectedKeys },
                                )
                                exitSelection()
                            },
                        ) { Text("转为下载") }
                        IconButton(
                            onClick = {
                                if (selectedKeys.isNotEmpty()) pendingDeleteKeys = selectedKeys
                            },
                            enabled = selectedKeys.isNotEmpty(),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "删除缓存",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { _, entry ->
        BanLiTrackListRow(
            track = entry.track,
            artwork = { artworkModifier -> CachedArtwork(entry, artworkModifier) },
            onClick = {
                if (selectionMode) {
                    selectedKeys = if (entry.cacheKey in selectedKeys) {
                        selectedKeys - entry.cacheKey
                    } else {
                        selectedKeys + entry.cacheKey
                    }
                } else {
                    selectionMode = true
                    selectedKeys = setOf(entry.cacheKey)
                }
            },
            onLongClick = {
                selectionMode = true
                selectedKeys = selectedKeys + entry.cacheKey
            },
            selected = if (selectionMode) entry.cacheKey in selectedKeys else null,
            extraBadges = {
                Text(
                    text = buildString {
                        append(
                            if (entry.qualityKbps > 0) {
                                qualityShortLabel(entry.qualityKbps)
                            } else {
                                "音质未知"
                            },
                        )
                        append(" · ")
                        append(formatBytes(entry.cachedBytes))
                        if (!entry.isComplete) append(" · 部分缓存")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailing = {
                if (state.audioCacheSupported) {
                    IconButton(
                        onClick = { pendingDeleteKeys = setOf(entry.cacheKey) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "删除缓存",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
    }

    pendingDeleteKeys?.let { keys ->
        AlertDialog(
            onDismissRequest = { pendingDeleteKeys = null },
            title = { Text("删除缓存歌曲") },
            text = { Text("将删除选中的 ${keys.size} 首缓存音频，已下载文件不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        keys.forEach(actions::removeCachedTrack)
                        selectedKeys = selectedKeys - keys
                        if (selectedKeys.isEmpty()) selectionMode = false
                        pendingDeleteKeys = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteKeys = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CachedArtwork(entry: CachedTrack, modifier: Modifier) {
    val palette = listOf(
        Color(0xFF0F766E) to Color(0xFF134E4A),
        Color(0xFF9A5B2C) to Color(0xFF5C3317),
        Color(0xFF3E5C8A) to Color(0xFF1F2E4A),
    )[(entry.track.title.hashCode() and Int.MAX_VALUE) % 3]
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(palette.first, palette.second))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}

@Composable
private fun SyncSettingsPage(state: SettingsUiState, actions: SettingsActions) {
    val sync = state.sync
    var showSignOut by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BanLiSettingsCard(title = "OneDrive 云同步", leading = { SettingsIcon(Icons.Rounded.CloudQueue) }) {
            Text(
                text = "收藏夹与收藏歌曲会保存到你自己 OneDrive 的应用专属目录：" +
                    "“应用 / BanLiMusic / library.json”。应用不会读取该目录之外的任何文件，" +
                    "数据也不会经过开发者服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!sync.supported) {
                HorizontalDivider()
                Text(
                    text = "当前平台暂不支持云同步",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = sync.unavailableReason ?: "请使用支持 OneDrive 同步的平台。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!sync.configured) {
                HorizontalDivider()
                Text(
                    text = "尚未配置 OneDrive 应用 ID",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (state.platform == SettingsPlatform.DESKTOP) {
                        "请先在 Microsoft Entra 注册应用，然后把 Application (client) ID 填入 " +
                            "桌面端配置。注册时选择“个人 Microsoft 账号”，在“移动和桌面应用程序”" +
                            "中添加下面的回调 URI，并允许公共客户端流；再授予 " +
                            "Files.ReadWrite.AppFolder、User.Read、offline_access 权限。"
                    } else {
                        "请先在 Microsoft Entra 注册应用，然后把 Application (client) ID 填入 " +
                            "OneDriveConfig.CLIENT_ID。注册时选择“个人 Microsoft 账号”，" +
                            "添加 Android 平台并填写下面的包名和签名，再授予 " +
                            "Files.ReadWrite.AppFolder、User.Read、offline_access 权限。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.platform == SettingsPlatform.ANDROID) {
                    CopyRow(
                        label = "签名 SHA-1（十六进制）",
                        value = sync.signatureSha1,
                        actions = actions,
                    )
                    CopyRow(
                        label = "签名哈希（SHA-1 Base64）",
                        value = sync.signatureHash,
                        actions = actions,
                    )
                }
                CopyRow(label = "回调 URI", value = sync.redirectUri, actions = actions)
            } else {
                HorizontalDivider()
                when (sync.auth) {
                    SyncAuthState.NOT_CONFIGURED -> Unit
                    SyncAuthState.SIGNED_OUT -> {
                        Text(
                            text = "登录后可在这台设备与其他设备之间双向同步收藏夹。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = actions::signInSync) {
                            Icon(
                                Icons.Rounded.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("登录 Microsoft 账号")
                        }
                    }

                    SyncAuthState.SIGNED_IN -> {
                        Text(
                            text = "已连接：${sync.accountName ?: "Microsoft 账号"}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "上次同步：${formatSyncTime(sync.lastSyncAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = actions::syncNow, enabled = !sync.syncing) {
                                if (sync.syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (sync.syncing) "同步中…" else "立即同步")
                            }
                            TextButton(onClick = { showSignOut = true }) { Text("断开连接") }
                        }
                        sync.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        sync.successText?.takeIf { it.isNotBlank() }?.let { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                        BanLiSettingsSwitchRow(
                            icon = Icons.Rounded.Refresh,
                            title = "自动同步",
                            description = if (state.platform == SettingsPlatform.DESKTOP) {
                                "启动应用时检查一次"
                            } else {
                                "启动应用时检查一次，并在后台定期同步"
                            },
                            checked = sync.autoSync,
                            onCheckedChange = actions::setAutoSync,
                        )
                        if (sync.autoSync) {
                            Text(text = "同步间隔", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 6, 12, 24).forEach { hours ->
                                    FilterChip(
                                        selected = sync.intervalHours == hours,
                                        onClick = { actions.setSyncIntervalHours(hours) },
                                        label = { Text("${hours} 小时") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.platform == SettingsPlatform.DESKTOP && sync.redirectUri.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = "桌面端回调 URI（需添加到 Entra 的“移动和桌面应用程序”）：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CopyRow(label = "桌面回调 URI", value = sync.redirectUri, actions = actions)
            }

            HorizontalDivider()
            Text(
                text = "建议在 Wi-Fi 下同步。云端文件只包含收藏夹名称、曲目 ID 与曲目元数据；" +
                    "不包含音频文件、播放历史或登录令牌。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text("断开 OneDrive") },
            text = {
                Text(
                    "断开后本机收藏仍会保留。选择“断开并删除云端文件”会删除 OneDrive 中的应用同步文件，" +
                        "其他设备上的本地数据不会被修改。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.signOutSync(deleteCloudFile = true)
                        showSignOut = false
                    },
                ) { Text("断开并删除云端文件") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            actions.signOutSync(deleteCloudFile = false)
                            showSignOut = false
                        },
                    ) { Text("仅断开") }
                    TextButton(onClick = { showSignOut = false }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun CopyRow(label: String, value: String, actions: SettingsActions) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { actions.copyText(label, value) }) { Text("复制") }
    }
}

@Composable
private fun AboutSettings(state: SettingsUiState, actions: SettingsActions) {
    var showAgreement by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BanLiSettingsCard(title = "关于 BanLiMusic", leading = { SettingsIcon(Icons.Rounded.Info) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.banli_logo),
                    contentDescription = "BanLiMusic 标志",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "BanLiMusic ${state.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "板栗音乐",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "音乐数据来自 GD 音乐台（music.gdstudio.xyz）。该来源仅用于学习参考，" +
                    "不得用于商业用途。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "请遵守服务端限流、鉴权与版权要求；播放地址仅做短时缓存，" +
                    "下载内容请自行确认授权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showAgreement = true }) {
                Text("重新阅读用户协议与 GD 音乐台声明")
            }
        }
    }

    if (showAgreement) {
        AlertDialog(
            onDismissRequest = { showAgreement = false },
            title = { Text("用户协议与 GD 音乐台声明") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AgreementContent()
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgreement = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
}

private fun hslOf(color: Color): FloatArray {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val lightness = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) {
        delta / (2f - max - min)
    } else {
        delta / (max + min)
    }
    val hue = when (max) {
        color.red -> (color.green - color.blue) / delta +
            if (color.green < color.blue) 6f else 0f
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
    } * 60f
    return floatArrayOf(hue, saturation, lightness)
}
