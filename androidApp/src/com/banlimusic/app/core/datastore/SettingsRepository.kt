package com.banlimusic.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val qualityKbps: Int = 320,
    val enabledSources: Set<String> = setOf("netease"),
    val allowMobileNetwork: Boolean = true,
    val autoSwitchSource: Boolean = true,
)

val SUPPORTED_SOURCES = listOf(
    SourceOption("netease", "网易云音乐"),
    SourceOption("joox", "JOOX"),
    SourceOption("bilibili", "哔哩哔哩"),
)

data class SourceOption(val id: String, val label: String)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val quality = intPreferencesKey("quality_kbps")
        val sources = stringSetPreferencesKey("enabled_sources")
        val allowMobileNetwork = booleanPreferencesKey("allow_mobile_network")
        val autoSwitchSource = booleanPreferencesKey("auto_switch_source")
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
                qualityKbps = preferences[Keys.quality] ?: 320,
                enabledSources = preferences[Keys.sources] ?: setOf("netease"),
                allowMobileNetwork = preferences[Keys.allowMobileNetwork] ?: true,
                autoSwitchSource = preferences[Keys.autoSwitchSource] ?: true,
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

    suspend fun setAutoSwitchSource(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.autoSwitchSource] = enabled
    }
}
