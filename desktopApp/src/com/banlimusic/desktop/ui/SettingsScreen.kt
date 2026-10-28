package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.banlimusic.app.core.cache.CacheUsage
import com.banlimusic.app.core.datastore.TabletMiniPlayerPosition
import com.banlimusic.app.core.datastore.TabletNavPosition
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.desktop.DesktopPaths
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.shared.ui.screens.BanLiSettingsScreen
import com.banlimusic.shared.ui.screens.SettingsActions
import com.banlimusic.shared.ui.screens.SettingsUiState
import com.banlimusic.shared.ui.screens.SyncUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane

/** Desktop adapter for the shared settings screen. */
@Composable
fun DesktopSettingsScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var rateStatus by remember { mutableStateOf<RateLimitStatus?>(null) }
    var cacheBytes by remember { mutableStateOf(0L) }

    fun refreshCacheUsage() {
        scope.launch {
            cacheBytes = withContext(Dispatchers.IO) {
                state.container.metadataCache.sizeBytes()
            }
        }
    }

    fun refreshRateStatus() {
        scope.launch {
            rateStatus = runCatching { state.container.musicRepository.rateLimitStatus() }
                .getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshCacheUsage()
        refreshRateStatus()
    }

    val actions = remember(state) {
        object : SettingsActions {
            override fun setThemeMode(mode: ThemeMode) =
                state.container.settingsRepository.setThemeMode(mode)

            override fun setDynamicColor(enabled: Boolean) =
                state.container.settingsRepository.setDynamicColor(enabled)

            override fun setCustomThemeColor(color: Int?) =
                state.container.settingsRepository.setCustomThemeColor(color)

            override fun setHighRefreshRateLists(enabled: Boolean) =
                state.container.settingsRepository.setHighRefreshRateLists(enabled)

            override fun setBottomNavItems(items: List<String>) =
                state.container.settingsRepository.setBottomNavItems(items)

            override fun setDefaultNavItem(item: String) =
                state.container.settingsRepository.setDefaultNavItem(item)

            override fun setTabletNavPosition(position: TabletNavPosition) =
                state.container.settingsRepository.setTabletNavPosition(position)

            override fun setTabletMiniPlayerPosition(position: TabletMiniPlayerPosition) =
                state.container.settingsRepository.setTabletMiniPlayerPosition(position)

            override fun setWidgetTheme(theme: WidgetTheme) =
                state.container.settingsRepository.setWidgetTheme(theme)

            override fun setAllowMobileNetwork(enabled: Boolean) =
                state.container.settingsRepository.setAllowMobileNetwork(enabled)

            override fun setDefaultShowLyrics(enabled: Boolean) =
                state.container.settingsRepository.setDefaultShowLyrics(enabled)

            override fun setOpenPlayerOnTrackClick(enabled: Boolean) =
                state.container.settingsRepository.setOpenPlayerOnTrackClick(enabled)

            override fun setAutoSwitchPlaybackSource(enabled: Boolean) =
                state.container.settingsRepository.setAutoSwitchPlaybackSource(enabled)

            override fun setPlaybackRetryCount(count: Int) =
                state.container.settingsRepository.setPlaybackRetryCount(count)

            override fun setConsecutiveFailureLimit(limit: Int) =
                state.container.settingsRepository.setConsecutiveFailureLimit(limit)

            override fun setQuality(kbps: Int) =
                state.container.settingsRepository.setPlaybackQuality(kbps)

            override fun setSourceEnabled(source: String, enabled: Boolean) =
                state.container.settingsRepository.setSourceEnabled(source, enabled)

            override fun setApiLimit(limit: Int) =
                state.container.settingsRepository.setApiLimit(limit)

            override fun setSearchPageSize(size: Int) =
                state.container.settingsRepository.setSearchPageSize(size)

            override fun setDownloadDirectory(path: String) =
                state.container.settingsRepository.setDownloadDirectory(path)

            override fun chooseDownloadDirectory() {
                val current = state.settings.value.downloadDirectory.ifBlank {
                    DesktopPaths.downloadDir.toString()
                }
                chooseDirectory(current, "选择下载目录")?.let {
                    state.container.settingsRepository.setDownloadDirectory(it)
                }
            }

            override fun setDownloadQuality(kbps: Int) =
                state.container.settingsRepository.setDownloadQuality(kbps)

            override fun setDownloadLyrics(enabled: Boolean) =
                state.container.settingsRepository.setDownloadLyrics(enabled)

            override fun setDownloadArtwork(enabled: Boolean) =
                state.container.settingsRepository.setDownloadArtwork(enabled)

            override fun setRequestListArtwork(enabled: Boolean) =
                state.container.settingsRepository.setRequestListArtwork(enabled)

            override fun setAutoLoadArtwork(enabled: Boolean) =
                state.container.settingsRepository.setAutoLoadArtwork(enabled)

            override fun setCacheMaxBytes(bytes: Long) =
                state.container.settingsRepository.setCacheMaxBytes(bytes)

            override fun clearArtworkCache() {
                scope.launch {
                    state.container.metadataCache.clear()
                    state.container.artworkLoader.clearMemory()
                    refreshCacheUsage()
                }
            }

            override fun clearLyricsCache() = clearArtworkCache()

            override fun clearOtherCache() {
                scope.launch {
                    state.container.musicRepository.clearCaches()
                    refreshCacheUsage()
                }
            }

            override fun clearAllCaches() {
                scope.launch {
                    state.container.metadataCache.clear()
                    state.container.artworkLoader.clearMemory()
                    state.container.musicRepository.clearCaches()
                    refreshCacheUsage()
                }
            }

            override fun refreshCacheUsage() = refreshCacheUsage()

            override fun refreshRateStatus() = refreshRateStatus()

            override fun setDefaultFavoriteCategory(category: String) =
                state.container.settingsRepository.setDefaultFavoriteCategory(category)

            override fun setAutoImportLocalMusic(enabled: Boolean) =
                state.container.settingsRepository.setAutoImportLocalMusic(enabled)

            override fun exportFavorites() {
                chooseSaveFile("banlimusic-favorites.json")?.let { file ->
                    scope.launch {
                        val raw = runCatching {
                            state.container.libraryRepository.exportFavoritesJson()
                        }.getOrNull()
                        val ok = raw != null && runCatching { file.writeText(raw) }.isSuccess
                        JOptionPane.showMessageDialog(
                            null,
                            if (ok) "收藏已导出" else "导出失败",
                        )
                    }
                }
            }

            override fun importFavorites(replace: Boolean) {
                chooseOpenFile()?.let { file ->
                    scope.launch {
                        val raw = runCatching { file.readText() }.getOrNull()
                        if (raw.isNullOrBlank()) {
                            JOptionPane.showMessageDialog(null, "无法读取收藏文件")
                        } else {
                            val count = state.container.libraryRepository
                                .importFavoritesJson(raw, replace)
                            JOptionPane.showMessageDialog(
                                null,
                                if (replace) "已覆盖导入 $count 首收藏" else "已追加 $count 首收藏",
                            )
                        }
                    }
                }
            }

            override fun clearSearchHistory() {
                scope.launch { state.container.libraryRepository.clearSearchHistory() }
            }

            override fun copyText(label: String, value: String) {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection(value), null)
            }
        }
    }

    BanLiSettingsScreen(
        state = SettingsUiState(
            settings = settings,
            versionName = desktopVersionName(),
            rateStatus = rateStatus,
            cacheUsage = CacheUsage(
                totalBytes = cacheBytes,
                artworkBytes = cacheBytes,
            ),
            cachedTracks = emptyList(),
            sync = SyncUiState(
                supported = false,
                unavailableReason = "当前 OneDrive 应用注册只包含 Android 平台的签名回调，" +
                    "桌面端需要单独的回调地址。请在 Android 端登录并同步；桌面端的收藏夹结构" +
                    "与之相同，可通过收藏备份迁移。",
            ),
            playbackQualities = DesktopSettingsRepository.DESKTOP_QUALITIES,
            downloadQualities = DesktopSettingsRepository.DESKTOP_QUALITIES,
            widgetSupported = false,
            audioCacheSupported = false,
            supportsDirectoryPicker = true,
        ),
        actions = actions,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun desktopVersionName(): String =
    SettingsUiState::class.java.`package`?.implementationVersion
        ?: System.getProperty("banlimusic.version")
        ?: "未知版本"

private fun chooseDirectory(initial: String, title: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        currentDirectory = File(initial).takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun chooseSaveFile(name: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "导出收藏"
        selectedFile = File(name)
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun chooseOpenFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "导入收藏"
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("收藏备份 (JSON)", "json")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}
