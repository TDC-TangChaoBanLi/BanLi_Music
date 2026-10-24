package com.banlimusic.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.cache.CacheRepository
import com.banlimusic.app.core.cache.CacheUsage
import com.banlimusic.app.core.cache.CachedTrack
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.CustomSourceSettings
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.core.model.ProviderInfo
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val cacheRepository: CacheRepository,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _rateStatus = MutableStateFlow<RateLimitStatus?>(null)
    val rateStatus: StateFlow<RateLimitStatus?> = _rateStatus.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers: StateFlow<List<ProviderInfo>> = _providers.asStateFlow()

    private val _cacheUsage = MutableStateFlow(CacheUsage())
    val cacheUsage: StateFlow<CacheUsage> = _cacheUsage.asStateFlow()

    val cachedTracks: StateFlow<List<CachedTrack>> = cacheRepository.observeCachedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshRateStatus()
        refreshProviders()
        refreshCacheUsage()
    }

    fun refreshRateStatus() {
        viewModelScope.launch {
            _rateStatus.value = runCatching { music.rateLimitStatus() }.getOrNull()
        }
    }

    fun refreshProviders() {
        viewModelScope.launch {
            _providers.value = runCatching { music.providers() }.getOrDefault(emptyList())
        }
    }

    fun refreshCacheUsage() {
        viewModelScope.launch {
            _cacheUsage.value = cacheRepository.usage()
            cacheRepository.refreshSizes()
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun setQuality(qualityKbps: Int) = viewModelScope.launch { settings.setQuality(qualityKbps) }

    fun setSourceEnabled(source: String, enabled: Boolean) =
        viewModelScope.launch { settings.setSourceEnabled(source, enabled) }

    fun setAllowMobileNetwork(enabled: Boolean) =
        viewModelScope.launch { settings.setAllowMobileNetwork(enabled) }

    fun setPlaybackRetryCount(count: Int) =
        viewModelScope.launch { settings.setPlaybackRetryCount(count) }

    fun setConsecutiveFailureLimit(limit: Int) =
        viewModelScope.launch { settings.setConsecutiveFailureLimit(limit) }

    fun setDefaultShowLyrics(enabled: Boolean) =
        viewModelScope.launch { settings.setDefaultShowLyrics(enabled) }

    fun setSearchPageSize(size: Int) =
        viewModelScope.launch { settings.setSearchPageSize(size) }

    fun setApiLimit(limit: Int) =
        viewModelScope.launch { settings.setApiLimitPerWindow(limit) }

    fun setDownloadDirectory(path: String) =
        viewModelScope.launch { settings.setDownloadDirectory(path) }

    fun setDownloadQuality(qualityKbps: Int) =
        viewModelScope.launch { settings.setDownloadQuality(qualityKbps) }

    fun setDownloadLyrics(enabled: Boolean) =
        viewModelScope.launch { settings.setDownloadLyrics(enabled) }

    fun setDownloadArtwork(enabled: Boolean) =
        viewModelScope.launch { settings.setDownloadArtwork(enabled) }

    fun setRequestListArtwork(enabled: Boolean) =
        viewModelScope.launch { settings.setRequestListArtwork(enabled) }

    fun setCacheMaxBytes(bytes: Long) {
        viewModelScope.launch {
            settings.setCacheMaxBytes(bytes)
            cacheRepository.trimTo(bytes)
            refreshCacheUsage()
        }
    }

    fun setBottomNavItems(items: List<String>) =
        viewModelScope.launch { settings.setBottomNavItems(items) }

    fun setDefaultNavItem(item: String) =
        viewModelScope.launch { settings.setDefaultNavItem(item) }

    fun setCustomSources(sources: List<CustomSourceSettings>) =
        viewModelScope.launch { settings.setCustomSources(sources) }

    fun setAgreementAccepted(accepted: Boolean) =
        viewModelScope.launch { settings.setAgreementAccepted(accepted) }

    fun setDefaultFavoriteCategory(category: String) =
        viewModelScope.launch { settings.setDefaultFavoriteCategory(category) }

    fun setAutoImportLocalMusic(enabled: Boolean) =
        viewModelScope.launch { settings.setAutoImportLocalMusic(enabled) }

    fun setWidgetTheme(theme: WidgetTheme) =
        viewModelScope.launch { settings.setWidgetTheme(theme) }

    fun setHighRefreshRateLists(enabled: Boolean) =
        viewModelScope.launch { settings.setHighRefreshRateLists(enabled) }

    fun setOpenPlayerOnTrackClick(enabled: Boolean) =
        viewModelScope.launch { settings.setOpenPlayerOnTrackClick(enabled) }

    fun clearSearchHistory() = viewModelScope.launch { library.clearSearchHistory() }

    suspend fun exportFavoritesJson(): String = library.exportFavoritesJson()

    fun importFavoritesJson(raw: String, replace: Boolean) {
        viewModelScope.launch {
            val count = runCatching { library.importFavoritesJson(raw, replace) }
                .getOrDefault(0)
            _message.value = if (replace) {
                "已覆盖导入 $count 首收藏"
            } else {
                "已追加 $count 首收藏"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun clearCaches() {
        viewModelScope.launch {
            music.clearCaches()
            cacheRepository.clearAll()
            refreshCacheUsage()
            refreshRateStatus()
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            cacheRepository.clearAudio()
            refreshCacheUsage()
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            cacheRepository.clearArtwork()
            refreshCacheUsage()
        }
    }

    fun clearLyricsCache() {
        viewModelScope.launch {
            cacheRepository.clearLyrics()
            refreshCacheUsage()
        }
    }

    fun clearOtherCache() {
        viewModelScope.launch {
            cacheRepository.clearOther()
            refreshCacheUsage()
        }
    }

    fun removeCachedTrack(cacheKey: String) {
        viewModelScope.launch {
            cacheRepository.remove(cacheKey)
            refreshCacheUsage()
        }
    }

    fun convertCachedToDownloads(entries: Collection<CachedTrack>) {
        viewModelScope.launch {
            val count = runCatching { cacheRepository.convertToDownloads(entries) }
                .getOrDefault(0)
            _message.value = if (count > 0) "已加入 $count 首下载" else "没有可转换的缓存歌曲"
            refreshCacheUsage()
        }
    }
}
