package com.banlimusic.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.cache.CacheManager
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.limiter.RateLimitStatus
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
    private val cacheManager: CacheManager,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _rateStatus = MutableStateFlow<RateLimitStatus?>(null)
    val rateStatus: StateFlow<RateLimitStatus?> = _rateStatus.asStateFlow()

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    init {
        refreshRateStatus()
        refreshCacheSize()
    }

    fun refreshRateStatus() {
        viewModelScope.launch {
            _rateStatus.value = runCatching { music.rateLimitStatus() }.getOrNull()
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = withContext(Dispatchers.IO) { cacheManager.sizeBytes() }
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

    fun setCacheMaxBytes(bytes: Long) {
        viewModelScope.launch {
            settings.setCacheMaxBytes(bytes)
            withContext(Dispatchers.IO) { cacheManager.trimTo(bytes) }
            refreshCacheSize()
        }
    }

    fun setBottomNavItems(items: List<String>) =
        viewModelScope.launch { settings.setBottomNavItems(items) }

    fun setAgreementAccepted(accepted: Boolean) =
        viewModelScope.launch { settings.setAgreementAccepted(accepted) }

    fun setDefaultFavoriteCategory(category: String) =
        viewModelScope.launch { settings.setDefaultFavoriteCategory(category) }

    fun setAutoImportLocalMusic(enabled: Boolean) =
        viewModelScope.launch { settings.setAutoImportLocalMusic(enabled) }

    fun setWidgetTheme(theme: WidgetTheme) =
        viewModelScope.launch { settings.setWidgetTheme(theme) }

    fun clearSearchHistory() = viewModelScope.launch { library.clearSearchHistory() }

    fun clearCaches() {
        viewModelScope.launch {
            music.clearCaches()
            withContext(Dispatchers.IO) { cacheManager.clear() }
            refreshCacheSize()
            refreshRateStatus()
        }
    }
}
