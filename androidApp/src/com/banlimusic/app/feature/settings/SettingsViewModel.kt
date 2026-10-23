package com.banlimusic.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.core.limiter.RateLimitStatus
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val music: MusicRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _rateStatus = MutableStateFlow<RateLimitStatus?>(null)
    val rateStatus: StateFlow<RateLimitStatus?> = _rateStatus.asStateFlow()

    init {
        refreshRateStatus()
    }

    fun refreshRateStatus() {
        viewModelScope.launch { _rateStatus.value = music.rateLimitStatus() }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun setQuality(qualityKbps: Int) = viewModelScope.launch { settings.setQuality(qualityKbps) }

    fun setSourceEnabled(source: String, enabled: Boolean) =
        viewModelScope.launch { settings.setSourceEnabled(source, enabled) }

    fun setAllowMobileNetwork(enabled: Boolean) =
        viewModelScope.launch { settings.setAllowMobileNetwork(enabled) }

    fun setAutoSwitchSource(enabled: Boolean) =
        viewModelScope.launch { settings.setAutoSwitchSource(enabled) }

    fun clearSearchHistory() = viewModelScope.launch { library.clearSearchHistory() }

    fun clearCaches() {
        music.clearCaches()
        refreshRateStatus()
    }
}
