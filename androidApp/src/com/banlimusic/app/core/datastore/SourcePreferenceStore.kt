/**
 * 文件作用：按曲目保存用户选择的优先播放来源。
 */
package com.banlimusic.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.LinkedHashMap

private val Context.sourcePreferenceDataStore by preferencesDataStore(
    name = "source_preferences",
)

/**
 * Keeps one preferred provider per application track id. The map is bounded so
 * a long listening history cannot grow the settings file without limit.
 */
class SourcePreferenceStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    private object Keys {
        val preferredSources = stringPreferencesKey("preferred_sources")
    }

    val preferredSources: Flow<Map<String, String>> = context.sourcePreferenceDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> decode(preferences[Keys.preferredSources]) }

    suspend fun preferredFor(trackId: String): String? =
        decode(context.sourcePreferenceDataStore.data.first()[Keys.preferredSources])[trackId]

    suspend fun setPreferred(trackId: String, providerId: String?) {
        if (trackId.isBlank()) return
        context.sourcePreferenceDataStore.edit { preferences ->
            val current = LinkedHashMap(decode(preferences[Keys.preferredSources]))
            current.remove(trackId)
            if (!providerId.isNullOrBlank()) current[trackId] = providerId
            while (current.size > MAX_ENTRIES) {
                val oldest = current.keys.firstOrNull() ?: break
                current.remove(oldest)
            }
            preferences[Keys.preferredSources] = json.encodeToString(serializer, current)
        }
    }

    private fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyMap())
            .filterValues { it.isNotBlank() }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
