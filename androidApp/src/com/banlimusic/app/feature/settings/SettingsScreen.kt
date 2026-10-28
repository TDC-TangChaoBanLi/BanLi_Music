/**
 * 文件作用：设置页的 Android 适配层，向共享设置界面提供设置读写、缓存、导入导出与云同步。
 */
package com.banlimusic.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.datastore.TabletMiniPlayerPosition
import com.banlimusic.app.core.datastore.TabletNavPosition
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.data.sync.AuthState
import com.banlimusic.app.data.sync.SyncStatus
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.shared.ui.screens.BanLiSettingsScreen
import com.banlimusic.shared.ui.screens.SettingsActions
import com.banlimusic.shared.ui.screens.SettingsScreenController
import com.banlimusic.shared.ui.screens.SettingsUiState
import com.banlimusic.shared.ui.screens.SyncAuthState
import com.banlimusic.shared.ui.screens.SyncUiState as SharedSyncUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onExitApp: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    syncViewModel: SyncViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val rateStatus by viewModel.rateStatus.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val settingsMessage by viewModel.message.collectAsStateWithLifecycle()
    val syncState by syncViewModel.state.collectAsStateWithLifecycle()
    val syncMessage by syncViewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val controller = remember { SettingsScreenController() }
    val scope = rememberCoroutineScope()
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "未知版本"
    }
    var pendingImportReplace by remember { mutableStateOf(false) }

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
                    viewModel.importFavoritesJson(raw, replace = pendingImportReplace)
                }
            }
        }
    }

    BackHandler { if (!controller.handleBack()) onExitApp() }

    LaunchedEffect(settingsMessage) {
        settingsMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            syncViewModel.consumeMessage()
        }
    }

    val actions = remember(
        viewModel,
        syncViewModel,
        context,
        exportLauncher,
        importLauncher,
    ) {
        object : SettingsActions {
            override fun setThemeMode(mode: ThemeMode) { viewModel.setThemeMode(mode) }
            override fun setDynamicColor(enabled: Boolean) { viewModel.setDynamicColor(enabled) }
            override fun setCustomThemeColor(color: Int?) {
                viewModel.setCustomThemeColor(color)
            }
            override fun setHighRefreshRateLists(enabled: Boolean) {
                viewModel.setHighRefreshRateLists(enabled)
            }
            override fun setBottomNavItems(items: List<String>) {
                viewModel.setBottomNavItems(items)
            }
            override fun setDefaultNavItem(item: String) { viewModel.setDefaultNavItem(item) }
            override fun setTabletNavPosition(position: TabletNavPosition) {
                viewModel.setTabletNavPosition(position)
            }
            override fun setTabletMiniPlayerPosition(position: TabletMiniPlayerPosition) {
                viewModel.setTabletMiniPlayerPosition(position)
            }
            override fun setWidgetTheme(theme: WidgetTheme) { viewModel.setWidgetTheme(theme) }
            override fun setAllowMobileNetwork(enabled: Boolean) {
                viewModel.setAllowMobileNetwork(enabled)
            }
            override fun setDefaultShowLyrics(enabled: Boolean) {
                viewModel.setDefaultShowLyrics(enabled)
            }
            override fun setOpenPlayerOnTrackClick(enabled: Boolean) {
                viewModel.setOpenPlayerOnTrackClick(enabled)
            }
            override fun setAutoSwitchPlaybackSource(enabled: Boolean) {
                viewModel.setAutoSwitchPlaybackSource(enabled)
            }
            override fun setPlaybackRetryCount(count: Int) {
                viewModel.setPlaybackRetryCount(count)
            }
            override fun setConsecutiveFailureLimit(limit: Int) {
                viewModel.setConsecutiveFailureLimit(limit)
            }
            override fun setQuality(kbps: Int) { viewModel.setQuality(kbps) }
            override fun setSourceEnabled(source: String, enabled: Boolean) {
                viewModel.setSourceEnabled(source, enabled)
            }
            override fun setApiLimit(limit: Int) { viewModel.setApiLimit(limit) }
            override fun setSearchPageSize(size: Int) { viewModel.setSearchPageSize(size) }
            override fun refreshRateStatus() { viewModel.refreshRateStatus() }
            override fun setDownloadDirectory(path: String) {
                viewModel.setDownloadDirectory(path)
            }
            override fun setDownloadQuality(kbps: Int) { viewModel.setDownloadQuality(kbps) }
            override fun setDownloadLyrics(enabled: Boolean) {
                viewModel.setDownloadLyrics(enabled)
            }
            override fun setDownloadArtwork(enabled: Boolean) {
                viewModel.setDownloadArtwork(enabled)
            }
            override fun setRequestListArtwork(enabled: Boolean) {
                viewModel.setRequestListArtwork(enabled)
            }
            override fun setAutoLoadArtwork(enabled: Boolean) {
                viewModel.setAutoLoadArtwork(enabled)
            }
            override fun setCacheMaxBytes(bytes: Long) { viewModel.setCacheMaxBytes(bytes) }
            override fun clearAudioCache() { viewModel.clearAudioCache() }
            override fun clearArtworkCache() { viewModel.clearArtworkCache() }
            override fun clearLyricsCache() { viewModel.clearLyricsCache() }
            override fun clearOtherCache() { viewModel.clearOtherCache() }
            override fun clearAllCaches() { viewModel.clearCaches() }
            override fun refreshCacheUsage() { viewModel.refreshCacheUsage() }
            override fun removeCachedTrack(cacheKey: String) {
                viewModel.removeCachedTrack(cacheKey)
            }
            override fun convertCachedToDownloads(
                entries: List<com.banlimusic.app.core.cache.CachedTrack>,
            ) {
                viewModel.convertCachedToDownloads(entries)
            }
            override fun setDefaultFavoriteCategory(category: String) {
                viewModel.setDefaultFavoriteCategory(category)
            }
            override fun setAutoImportLocalMusic(enabled: Boolean) {
                viewModel.setAutoImportLocalMusic(enabled)
            }
            override fun exportFavorites() {
                exportLauncher.launch("banlimusic-favorites.json")
            }
            override fun importFavorites(replace: Boolean) {
                pendingImportReplace = replace
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            override fun clearSearchHistory() { viewModel.clearSearchHistory() }
            override fun signInSync() { syncViewModel.signIn() }
            override fun signOutSync(deleteCloudFile: Boolean) {
                syncViewModel.signOut(deleteCloudFile)
            }
            override fun syncNow() { syncViewModel.syncNow() }
            override fun setAutoSync(enabled: Boolean) { syncViewModel.setAutoSync(enabled) }
            override fun setSyncIntervalHours(hours: Int) {
                syncViewModel.setIntervalHours(hours)
            }
            override fun copyText(label: String, value: String) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val syncStatus = syncState.status
    val success = syncStatus as? SyncStatus.Success
    val syncUi = SharedSyncUiState(
        supported = true,
        configured = syncState.configured,
        auth = when (syncState.auth) {
            AuthState.NOT_CONFIGURED -> SyncAuthState.NOT_CONFIGURED
            AuthState.SIGNED_OUT -> SyncAuthState.SIGNED_OUT
            AuthState.SIGNED_IN -> SyncAuthState.SIGNED_IN
        },
        accountName = syncState.accountName,
        signatureSha1 = syncState.signatureSha1,
        signatureHash = syncState.signatureHash,
        redirectUri = syncState.redirectUri,
        autoSync = syncState.autoSync,
        intervalHours = syncState.intervalHours,
        syncing = syncStatus is SyncStatus.Syncing,
        lastSyncAt = syncState.lastSyncAt,
        lastError = (syncStatus as? SyncStatus.Error)?.message ?: syncState.lastError,
        successAdded = success?.addedMemberships ?: 0,
        successRemoved = success?.removedMemberships ?: 0,
        successText = success?.let {
            buildString {
                append("同步完成")
                if (it.addedMemberships > 0) append("，新增 ${it.addedMemberships} 项")
                if (it.removedMemberships > 0) append("，移除 ${it.removedMemberships} 项")
            }
        },
    )

    BanLiSettingsScreen(
        state = SettingsUiState(
            settings = settings,
            versionName = versionName,
            rateStatus = rateStatus,
            cacheUsage = cacheUsage,
            cachedTracks = cachedTracks,
            sync = syncUi,
            playbackQualities = QualityTiers.PRESET_KBPS,
            downloadQualities = QualityTiers.PRESET_KBPS,
            widgetSupported = true,
            audioCacheSupported = true,
        ),
        actions = actions,
        modifier = Modifier.fillMaxSize(),
        controller = controller,
    )
}
