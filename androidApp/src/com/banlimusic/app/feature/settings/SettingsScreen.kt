/**
 * 文件作用：设置页界面，组织外观、播放、播放源、缓存与关于等模块。
 */
package com.banlimusic.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
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
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.R
import com.banlimusic.app.core.cache.CachedTrack
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.BOTTOM_NAV_OPTIONS
import com.banlimusic.app.core.datastore.MAX_CACHE_MAX_BYTES
import com.banlimusic.app.core.datastore.MIN_CACHE_MAX_BYTES
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.datastore.sanitizeNavItems
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.ProviderInfo
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.data.sync.AuthState
import com.banlimusic.app.data.sync.SyncStatus
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.app.ui.components.formatBytes
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.ui.components.qualityShortLabel
import com.banlimusic.app.widget.PlayerWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CacheClearTarget(val title: String, val message: String) {
    AUDIO(
        "清理歌曲缓存",
        "将删除已缓存的播放音频，已下载文件不受影响。",
    ),
    ARTWORK(
        "清理海报缓存",
        "将删除列表、系统媒体控件和桌面小组件使用的缓存海报。",
    ),
    LYRICS(
        "清理歌词缓存",
        "将删除已缓存的歌词文件。",
    ),
    OTHER(
        "清理其他缓存",
        "将删除 HTTP 请求、图片等临时缓存。",
    ),
    ALL(
        "清理全部缓存",
        "将删除歌曲、海报、歌词和其他缓存；不会删除已下载文件，正在下载的临时文件会保留。",
    ),
}

private enum class SettingsPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    APPEARANCE("外观", "主题、导航栏与桌面小组件", Icons.Rounded.Palette),
    PLAYBACK("播放", "失败重试、歌词默认页与网络策略", Icons.Rounded.PlayCircle),
    SOURCE("播放源", "GD 音乐台与预留的自定义 API 来源", Icons.Rounded.CloudQueue),
    DOWNLOAD_CACHE("下载与缓存", "下载目录、缓存上限与缓存歌曲", Icons.Rounded.Download),
    LIBRARY("音乐库与收藏", "默认收藏夹与本地音乐", Icons.Rounded.LibraryMusic),
    SYNC("云同步", "使用自己的 OneDrive 同步收藏夹", Icons.Rounded.CloudQueue),
    ABOUT("关于", "版本与来源声明", Icons.Rounded.Info),
    CACHED_TRACKS("缓存歌曲", "查看缓存歌曲并转换为下载", Icons.Rounded.QueueMusic),
}

private val HOME_PAGES = SettingsPage.entries.filter { it != SettingsPage.CACHED_TRACKS }
private val QUALITY_OPTIONS = QualityTiers.PRESET_KBPS
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

@Composable
fun SettingsScreen(
    onExitApp: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val rateStatus by viewModel.rateStatus.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = page == null) { onExitApp() }
    BackHandler(enabled = page != null) {
        page = if (page == SettingsPage.CACHED_TRACKS) SettingsPage.DOWNLOAD_CACHE else null
    }

    if (page == null) {
        SettingsHome(onSelect = { page = it })
    } else {
        SettingsDetail(
            page = page ?: return,
            settings = settings,
            rateStatus = rateStatus,
            providers = providers,
            cacheUsage = cacheUsage,
            cachedTracks = cachedTracks,
            viewModel = viewModel,
            onOpenCachedTracks = { page = SettingsPage.CACHED_TRACKS },
            onBack = {
                page = if (page == SettingsPage.CACHED_TRACKS) {
                    SettingsPage.DOWNLOAD_CACHE
                } else {
                    null
                }
            },
        )
    }
}

@Composable
private fun SettingsHome(onSelect: (SettingsPage) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(text = "设置", style = MaterialTheme.typography.headlineSmall)
        }
        items(HOME_PAGES) { page ->
            SettingsNavRow(page = page, onClick = { onSelect(page) })
        }
    }
}

@Composable
private fun SettingsNavRow(page: SettingsPage, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(page.icon)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDetail(
    page: SettingsPage,
    settings: AppSettings,
    rateStatus: RateLimitStatus?,
    providers: List<ProviderInfo>,
    cacheUsage: com.banlimusic.app.core.cache.CacheUsage,
    cachedTracks: List<CachedTrack>,
    viewModel: SettingsViewModel,
    onOpenCachedTracks: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "未知版本"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Icon(page.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when (page) {
            SettingsPage.CACHED_TRACKS -> CachedTracksPage(
                cachedTracks = cachedTracks,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    SettingsPage.APPEARANCE -> item {
                        AppearanceSettings(settings, viewModel)
                    }

                    SettingsPage.PLAYBACK -> item {
                        PlaybackSettings(settings, viewModel)
                    }

                    SettingsPage.SOURCE -> item {
                        SourceSettings(settings, rateStatus, providers, viewModel)
                    }

                    SettingsPage.DOWNLOAD_CACHE -> item {
                        DownloadCacheSettings(
                            settings = settings,
                            cacheUsage = cacheUsage,
                            viewModel = viewModel,
                            onOpenCachedTracks = onOpenCachedTracks,
                        )
                    }

                    SettingsPage.LIBRARY -> item {
                        LibrarySettings(settings, viewModel)
                    }

                    SettingsPage.SYNC -> item {
                        SyncSettingsPage()
                    }

                    SettingsPage.ABOUT -> item {
                        AboutSettings(versionName)
                    }

                    SettingsPage.CACHED_TRACKS -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var navItems by remember(settings.bottomNavItems) {
        mutableStateOf(sanitizeNavItems(settings.bottomNavItems))
    }

    fun commitNav(updated: List<String>) {
        navItems = updated
        viewModel.setBottomNavItems(updated)
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in navItems.indices) return
        val updated = navItems.toMutableList()
        val item = updated.removeAt(index)
        updated.add(target, item)
        commitNav(updated)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "主题与外观", icon = Icons.Rounded.Palette) {
            Text("主题模式", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(themeLabel(mode)) },
                    )
                }
            }
            SettingsSwitchRow(
                icon = Icons.Rounded.ColorLens,
                title = "动态取色",
                description = "Android 12+ 使用系统壁纸颜色；关闭后可自定义主题色",
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            SettingsSwitchRow(
                icon = Icons.Rounded.Speed,
                title = "列表高刷新率",
                description = "浏览列表时尝试使用 90Hz 显示模式",
                checked = settings.highRefreshRateLists,
                onCheckedChange = viewModel::setHighRefreshRateLists,
            )
            HorizontalDivider()
            if (settings.dynamicColor) {
                SettingsSectionLabel(Icons.Rounded.ColorLens, "自定义主题颜色")
                Text(
                    text = "关闭“动态取色”后才能自定义主题颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CustomThemeColorSection(settings = settings, viewModel = viewModel)
            }
        }

        SettingsCard(title = "底部导航栏", icon = Icons.Rounded.ViewList) {
            Text(
                "所有栏目始终显示，可调整左右顺序；默认栏目在下次启动时生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEach { id ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(navIcon(id), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = navLabel(id),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
            HorizontalDivider()
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
                        onClick = { move(index, -1) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) { Text("上移") }
                    TextButton(
                        enabled = index < navItems.lastIndex,
                        onClick = { move(index, 1) },
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
                        onClick = { viewModel.setDefaultNavItem(id) },
                        label = { Text(navLabel(id)) },
                    )
                }
            }
        }

        SettingsCard(title = "桌面小组件", icon = Icons.Rounded.Widgets) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.widgetTheme == theme,
                        onClick = {
                            viewModel.setWidgetTheme(theme)
                            scope.launch {
                                PlayerWidgetUpdater.setTheme(context, theme)
                            }
                        },
                        label = { Text(widgetThemeLabel(theme)) },
                    )
                }
            }
            Text(
                "3×1 显示歌名、歌手、播放控件与底部进度条；4×1 额外显示封面与收藏按钮。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomThemeColorSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val current = settings.customThemeColor
    val fallback = MaterialTheme.colorScheme.primary.toArgb()
    val hsv = remember(current, fallback) {
        FloatArray(3).also { AndroidColor.colorToHSV(current ?: fallback, it) }
    }
    var hue by remember(current, fallback) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(current, fallback) { mutableFloatStateOf(hsv[1]) }
    val preview = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(hue, saturation.coerceIn(0.15f, 1f), 0.78f),
        ),
    )
    val customSelected = current != null && current !in CUSTOM_THEME_SWATCHES

    SettingsSectionLabel(Icons.Rounded.ColorLens, "自定义主题颜色")
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
                    .clickable { viewModel.setCustomThemeColor(argb) }
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
                .clickable { viewModel.setCustomThemeColor(preview.toArgb()) }
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
            onValueChangeFinished = { viewModel.setCustomThemeColor(preview.toArgb()) },
        )
        Text("饱和度", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            valueRange = 0.15f..1f,
            onValueChangeFinished = { viewModel.setCustomThemeColor(preview.toArgb()) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSettings(settings: AppSettings, viewModel: SettingsViewModel) {
    SettingsCard(title = "播放行为", icon = Icons.Rounded.PlayCircle) {
        SettingsSwitchRow(
            icon = Icons.Rounded.Wifi,
            title = "允许移动网络播放",
            description = "关闭后仅在 Wi-Fi 等非计费网络下播放和下载",
            checked = settings.allowMobileNetwork,
            onCheckedChange = viewModel::setAllowMobileNetwork,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.Lyrics,
            title = "单曲页默认显示歌词",
            description = "关闭时默认显示海报，可左滑切换到歌词",
            checked = settings.defaultShowLyrics,
            onCheckedChange = viewModel::setDefaultShowLyrics,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.TouchApp,
            title = "点按歌曲进入播放页",
            description = "关闭后点按列表只播放，不自动打开全屏播放器",
            checked = settings.openPlayerOnTrackClick,
            onCheckedChange = viewModel::setOpenPlayerOnTrackClick,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.SwapHoriz,
            title = "播放失败自动换源",
            description = "当前来源播放失败时，按顺序尝试这首歌的其他来源",
            checked = settings.autoSwitchPlaybackSource,
            onCheckedChange = viewModel::setAutoSwitchPlaybackSource,
        )
        SettingsSlider(
            icon = Icons.Rounded.Replay,
            title = "失败自动重试次数",
            valueText = "${settings.playbackRetryCount} 次",
            value = settings.playbackRetryCount.toFloat(),
            valueRange = 0f..10f,
            steps = 9,
            onValueChangeFinished = { viewModel.setPlaybackRetryCount(it.toInt()) },
        )
        Text(
            "重试间隔依次为 2、4、8、16 秒。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSlider(
            icon = Icons.Rounded.ErrorOutline,
            title = "连续失败停止阈值",
            valueText = "${settings.consecutiveFailureLimit} 首",
            value = settings.consecutiveFailureLimit.toFloat(),
            valueRange = 1f..10f,
            steps = 8,
            onValueChangeFinished = { viewModel.setConsecutiveFailureLimit(it.toInt()) },
        )
        Text(
            "连续多首歌曲播放失败后会发送系统通知并停止播放。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        SettingsSectionLabel(Icons.Rounded.HighQuality, "播放音质")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUALITY_OPTIONS.forEach { quality ->
                FilterChip(
                    selected = settings.qualityKbps == quality,
                    onClick = { viewModel.setQuality(quality) },
                    label = { Text(qualityShortLabel(quality)) },
                )
            }
        }
        Text(
            "所有音乐源统一使用标准、较高、高品、无损四档；来源支持的档位不足时映射到最接近的音质，多于四档时舍弃多余档位。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceSettings(
    settings: AppSettings,
    rateStatus: RateLimitStatus?,
    providers: List<ProviderInfo>,
    viewModel: SettingsViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "GD 音乐台", icon = Icons.Rounded.CloudQueue) {
            Text(
                "GD 音乐台是当前内置播放源，多个二级音源共享同一后端限流额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val gdSources = providers
                .filter { it.id.startsWith("gd-") }
                .map { provider ->
                    val sourceKey = provider.id.removePrefix("gd-")
                    Triple(sourceKey, provider.name, provider.enabled)
                }
            val sourceRows = if (gdSources.isEmpty()) {
                SUPPORTED_SOURCES.map { Triple(it.id, it.label, it.id in settings.enabledSources) }
            } else {
                gdSources
            }
            sourceRows.forEach { (sourceKey, label, _) ->
                SettingsSwitchRow(
                    icon = Icons.Rounded.CloudQueue,
                    title = label,
                    description = "二级音源标识：$sourceKey",
                    checked = sourceKey in settings.enabledSources,
                    onCheckedChange = { viewModel.setSourceEnabled(sourceKey, it) },
                )
            }
            HorizontalDivider()
            SettingsSlider(
                icon = Icons.Rounded.Speed,
                title = "5 分钟请求上限",
                valueText = "${settings.apiLimitPerWindow} 次",
                value = settings.apiLimitPerWindow.toFloat(),
                valueRange = 1f..50f,
                steps = 48,
                onValueChangeFinished = { viewModel.setApiLimit(it.toInt()) },
            )
            Text(
                "GD 音乐台强制每次请求间隔至少 1 秒，服务端上限为 50 次/5 分钟。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsSectionLabel(Icons.Rounded.FormatListNumbered, "每次搜索/加载数量")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 50).forEach { size ->
                    FilterChip(
                        selected = settings.searchPageSize == size,
                        onClick = { viewModel.setSearchPageSize(size) },
                        label = { Text("$size 首") },
                    )
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rateStatusText(rateStatus),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::refreshRateStatus) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新状态")
                }
            }
        }

        SettingsCard(title = "自定义 API 来源（预留）", icon = Icons.Rounded.AddLink) {
            Text(
                "该模块为后续接入自建音乐服务器预留，当前不会发起任何自定义请求。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val futureItems = listOf(
                "二级音乐源名称与对应代码",
                "自定义曲目 ID 表示方式",
                "自定义音质代码及显示名称",
                "自定义搜索、获取音乐、歌词与海报 API 格式及参数映射",
            )
            futureItems.forEach { text ->
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
    settings: AppSettings,
    cacheUsage: com.banlimusic.app.core.cache.CacheUsage,
    viewModel: SettingsViewModel,
    onOpenCachedTracks: () -> Unit,
) {
    val context = LocalContext.current
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
        SettingsCard(title = "下载", icon = Icons.Rounded.Download) {
            OutlinedTextField(
                value = directory,
                onValueChange = { directory = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("下载目录（留空使用系统音乐目录）") },
                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                singleLine = true,
            )
            TextButton(onClick = { viewModel.setDownloadDirectory(directory) }) {
                Text("保存路径")
            }
            SettingsSectionLabel(Icons.Rounded.HighQuality, "默认下载音质")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUALITY_OPTIONS.forEach { quality ->
                    FilterChip(
                        selected = settings.downloadQualityKbps == quality,
                        onClick = { viewModel.setDownloadQuality(quality) },
                        label = { Text(qualityShortLabel(quality)) },
                    )
                }
            }
            SettingsSwitchRow(
                icon = Icons.Rounded.Subtitles,
                title = "同时下载歌词",
                description = "保存为与歌曲同名的 .lrc 文件",
                checked = settings.downloadLyrics,
                onCheckedChange = viewModel::setDownloadLyrics,
            )
            SettingsSwitchRow(
                icon = Icons.Rounded.Image,
                title = "同时下载海报",
                description = "保存为与歌曲同名的 .jpg 文件",
                checked = settings.downloadArtwork,
                onCheckedChange = viewModel::setDownloadArtwork,
            )
        }

        SettingsCard(title = "缓存", icon = Icons.Rounded.Storage) {
            SettingsSwitchRow(
                icon = Icons.Rounded.Image,
                title = "列表页请求海报",
                description = "关闭时列表只读取已缓存海报；播放页始终加载",
                checked = settings.requestListArtwork,
                onCheckedChange = viewModel::setRequestListArtwork,
            )
            SettingsSwitchRow(
                icon = Icons.Rounded.Image,
                title = "播放时自动加载海报",
                description = "当前歌曲没有海报缓存时自动请求，供系统媒体控件和桌面小组件显示",
                checked = settings.autoLoadArtwork,
                onCheckedChange = viewModel::setAutoLoadArtwork,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingsSectionLabel(Icons.Rounded.Storage, "缓存上限")
                    Text(
                        formatBytes(cacheUsage.totalBytes) + " / " + formatBytes(settings.cacheMaxBytes),
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
                        viewModel.setCacheMaxBytes(bytes)
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
                    viewModel.setCacheMaxBytes(bytes)
                },
            )
            SettingsValueRow(
                icon = Icons.Rounded.MusicNote,
                title = "歌曲缓存",
                value = formatBytes(cacheUsage.audioBytes),
                onClear = { pendingClear = CacheClearTarget.AUDIO },
            )
            SettingsValueRow(
                icon = Icons.Rounded.Image,
                title = "海报缓存",
                value = formatBytes(cacheUsage.artworkBytes),
                onClear = { pendingClear = CacheClearTarget.ARTWORK },
            )
            SettingsValueRow(
                icon = Icons.Rounded.Lyrics,
                title = "歌词缓存",
                value = formatBytes(cacheUsage.lyricsBytes),
                onClear = { pendingClear = CacheClearTarget.LYRICS },
            )
            SettingsValueRow(
                icon = Icons.Rounded.PieChart,
                title = "其他缓存",
                value = formatBytes(cacheUsage.otherBytes),
                onClear = { pendingClear = CacheClearTarget.OTHER },
            )
            SettingsActionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "缓存歌曲",
                description = "查看详细列表并转换为已下载歌曲",
                actionText = "查看",
                onClick = onOpenCachedTracks,
            )
            SettingsActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "清理全部缓存",
                description = "不会删除已下载文件；正在下载的临时文件会保留",
                actionText = "清理",
                onClick = { pendingClear = CacheClearTarget.ALL },
            )
            TextButton(onClick = viewModel::refreshCacheUsage) {
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
                            CacheClearTarget.AUDIO -> viewModel.clearAudioCache()
                            CacheClearTarget.ARTWORK -> viewModel.clearArtworkCache()
                            CacheClearTarget.LYRICS -> viewModel.clearLyricsCache()
                            CacheClearTarget.OTHER -> viewModel.clearOtherCache()
                            CacheClearTarget.ALL -> {
                                viewModel.clearCaches()
                                Toast.makeText(context, "已清理缓存", Toast.LENGTH_SHORT).show()
                            }
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

@Composable
private fun CachedTracksPage(
    cachedTracks: List<CachedTrack>,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleteKeys by remember { mutableStateOf<Set<String>?>(null) }
    val totalBytes = cachedTracks.sumOf { it.cachedBytes }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已缓存 ${cachedTracks.size} 首 · ${formatBytes(totalBytes)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    selected = if (selected.size == cachedTracks.size) {
                        emptySet()
                    } else {
                        cachedTracks.map { it.cacheKey }.toSet()
                    }
                },
            ) {
                Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (selected.size == cachedTracks.size) "取消全选" else "全选")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val entries = cachedTracks.filter { it.cacheKey in selected }
                    viewModel.convertCachedToDownloads(entries)
                    selected = emptySet()
                },
            ) { Text("转为下载") }
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    if (selected.isNotEmpty()) pendingDeleteKeys = selected
                },
            ) { Text("删除缓存") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::refreshCacheUsage) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("刷新")
            }
        }
        if (cachedTracks.isEmpty()) {
            EmptyState(
                title = "还没有缓存歌曲",
                description = "播放过的歌曲会出现在这里，可转换为已下载歌曲。",
                icon = Icons.Rounded.QueueMusic,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(cachedTracks, key = { it.cacheKey }) { entry ->
                    CachedTrackRow(
                        entry = entry,
                        selected = entry.cacheKey in selected,
                        onToggle = {
                            selected = if (entry.cacheKey in selected) {
                                selected - entry.cacheKey
                            } else {
                                selected + entry.cacheKey
                            }
                        },
                        onDelete = { pendingDeleteKeys = setOf(entry.cacheKey) },
                    )
                }
            }
        }
    }

    pendingDeleteKeys?.let { keys ->
        AlertDialog(
            onDismissRequest = { pendingDeleteKeys = null },
            title = { Text("删除缓存歌曲") },
            text = {
                Text("将删除选中的 ${keys.size} 首缓存音频，已下载文件不受影响。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cachedTracks.filter { it.cacheKey in keys }.forEach {
                            viewModel.removeCachedTrack(it.cacheKey)
                        }
                        selected = selected - keys
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
private fun CachedTrackRow(
    entry: CachedTrack,
    selected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        TrackArtwork(track = entry.track, modifier = Modifier.size(46.dp), cornerRadius = 9.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(
                text = entry.track.artistLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(
                        if (entry.qualityKbps > 0) qualityLabel(entry.qualityKbps) else "音质未知",
                    )
                    append(" · ")
                    append(formatBytes(entry.cachedBytes))
                    if (!entry.isComplete) append(" · 部分缓存")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "删除缓存",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LibrarySettings(settings: AppSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = runCatching { viewModel.exportFavoritesJson() }.getOrNull()
                val ok = raw != null && withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(raw.toByteArray())
                        } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(context, if (ok) "收藏已导出" else "导出失败", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            it.readText()
                        }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    Toast.makeText(context, "无法读取收藏文件", Toast.LENGTH_SHORT).show()
                } else {
                    pendingImport = raw
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "音乐库与收藏", icon = Icons.Rounded.LibraryMusic) {
            var defaultCategory by remember(settings.defaultFavoriteCategory) {
                mutableStateOf(settings.defaultFavoriteCategory)
            }
            OutlinedTextField(
                value = defaultCategory,
                onValueChange = { defaultCategory = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("默认收藏夹") },
                leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, contentDescription = null) },
                singleLine = true,
            )
            TextButton(onClick = { viewModel.setDefaultFavoriteCategory(defaultCategory) }) {
                Text("保存默认收藏夹")
            }
            SettingsSwitchRow(
                icon = Icons.Rounded.AudioFile,
                title = "启动时自动导入本地音乐",
                description = "需要媒体读取权限；只记录系统媒体库中的音频，不复制文件",
                checked = settings.autoImportLocalMusic,
                onCheckedChange = viewModel::setAutoImportLocalMusic,
            )
            Text(
                text = "收藏备份",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { exportLauncher.launch("banlimusic-favorites.json") }) {
                    Icon(Icons.Rounded.ImportExport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出收藏")
                }
                TextButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                ) { Text("导入收藏") }
            }
            Text(
                "导出文件包含收藏夹、每首歌所属的多个收藏夹、添加时间与曲目元数据。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            SettingsActionRow(
                icon = Icons.Rounded.Search,
                title = "清空搜索历史",
                description = "只清除搜索建议，不影响收藏和播放记录",
                actionText = "清空",
                onClick = {
                    viewModel.clearSearchHistory()
                    Toast.makeText(context, "已清空搜索历史", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    pendingImport?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入收藏") },
            text = {
                Column {
                    Text(
                        "选择“覆盖导入”会移除当前收藏（并在下次同步时同步到云端）；" +
                            "选择“仅追加”会保留现有收藏。",
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            viewModel.importFavoritesJson(raw, replace = true)
                            pendingImport = null
                        },
                    ) { Text("覆盖导入") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importFavoritesJson(raw, replace = false)
                        pendingImport = null
                    },
                ) { Text("仅追加") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncSettingsPage(
    syncViewModel: SyncViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by syncViewModel.state.collectAsStateWithLifecycle()
    val message by syncViewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            syncViewModel.consumeMessage()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "OneDrive 云同步", icon = Icons.Rounded.CloudQueue) {
            Text(
                text = "收藏夹与收藏歌曲会保存到你自己 OneDrive 的应用专属目录：" +
                    "“应用 / BanLiMusic / library.json”。应用不会读取该目录之外的任何文件，" +
                    "数据也不会经过开发者服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.configured) {
                HorizontalDivider()
                Text(
                    text = "尚未配置 OneDrive 应用 ID",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "请先在 Microsoft Entra 注册应用，然后把 Application (client) ID 填入 " +
                        "OneDriveConfig.CLIENT_ID。注册时选择“个人 Microsoft 账号”，" +
                        "添加 Android 平台并填写下面的包名和签名，再授予 " +
                        "Files.ReadWrite.AppFolder、User.Read、offline_access 权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CopyRow(label = "包名", value = context.packageName)
                CopyRow(label = "SHA-1 指纹（十六进制）", value = state.signatureSha1)
                CopyRow(label = "签名哈希（SHA-1 Base64）", value = state.signatureHash)
                CopyRow(label = "回调 URI", value = state.redirectUri)
            } else {
                HorizontalDivider()
                when (state.auth) {
                    AuthState.NOT_CONFIGURED -> Unit
                    AuthState.SIGNED_OUT -> {
                        Text(
                            text = "登录后可在这台设备与其他 Android 设备之间双向同步收藏夹。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = syncViewModel::signIn) {
                            Icon(
                                Icons.Rounded.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("登录 Microsoft 账号")
                        }
                    }

                    AuthState.SIGNED_IN -> {
                        Text(
                            text = "已连接：${state.accountName ?: "Microsoft 账号"}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "上次同步：${formatSyncTime(state.lastSyncAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val syncing = state.status == SyncStatus.Syncing
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = syncViewModel::syncNow, enabled = !syncing) {
                                if (syncing) {
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
                                Text(if (syncing) "同步中…" else "立即同步")
                            }
                            TextButton(onClick = { showSignOut = true }) { Text("断开连接") }
                        }
                        val failure = state.status as? SyncStatus.Error
                        val errorText = failure?.message ?: state.lastError
                        if (!errorText.isNullOrBlank()) {
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        val success = state.status as? SyncStatus.Success
                        if (success != null) {
                            Text(
                                text = buildString {
                                    append("同步完成")
                                    if (success.addedMemberships > 0) {
                                        append("，新增 ${success.addedMemberships} 项")
                                    }
                                    if (success.removedMemberships > 0) {
                                        append("，移除 ${success.removedMemberships} 项")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Refresh,
                            title = "自动同步",
                            description = "启动应用时检查一次，并在后台定期同步",
                            checked = state.autoSync,
                            onCheckedChange = syncViewModel::setAutoSync,
                        )
                        if (state.autoSync) {
                            Text(text = "同步间隔", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 6, 12, 24).forEach { hours ->
                                    FilterChip(
                                        selected = state.intervalHours == hours,
                                        onClick = { syncViewModel.setIntervalHours(hours) },
                                        label = { Text("${hours} 小时") },
                                    )
                                }
                            }
                        }
                    }
                }
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
                        syncViewModel.signOut(deleteCloudFile = true)
                        showSignOut = false
                    },
                ) { Text("断开并删除云端文件") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            syncViewModel.signOut(deleteCloudFile = false)
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
private fun CopyRow(label: String, value: String) {
    if (value.isBlank()) return
    val context = LocalContext.current
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
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
            },
        ) { Text("复制") }
    }
}

private fun formatSyncTime(value: Long): String =
    if (value <= 0L) {
        "尚未同步"
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
    }

@Composable
private fun AboutSettings(versionName: String) {
    SettingsCard(title = "关于 BanLiMusic", icon = Icons.Rounded.Info) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.banli_logo),
                contentDescription = "BanLiMusic 标志",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "BanLiMusic $versionName",
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
            text = "音乐数据来自 GD 音乐台（music.gdstudio.xyz）。该来源仅用于学习参考，不得用于商业用途。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "请遵守服务端限流、鉴权与版权要求；播放地址仅做短时缓存，下载内容请自行确认授权。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "未来将支持自建音乐服务器与本地音乐；桌面端共享核心已接入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, size: Int = 34) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun SettingsSectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSlider(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished(current) },
        )
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "清理$title",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text(actionText) }
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun widgetThemeLabel(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.SYSTEM -> "跟随系统"
    WidgetTheme.BLACK -> "黑色磨砂"
    WidgetTheme.WHITE -> "白色磨砂"
}

private fun rateStatusText(status: RateLimitStatus?): String {
    if (status == null) return "限流状态：暂无数据"
    val resetSeconds = (status.windowResetInMs / 1000L).coerceAtLeast(0L)
    val cooldown = (status.cooldownRemainingMs / 1000L).coerceAtLeast(0L)
    return buildString {
        append("限流：${status.used}/${status.limit} 次")
        append("，剩余 ${status.remaining} 次")
        if (resetSeconds > 0) append("，${resetSeconds}s 后重置")
        if (cooldown > 0) append("，冷却 ${cooldown}s")
    }
}
