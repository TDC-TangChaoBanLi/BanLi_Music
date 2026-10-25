/**
 * 文件作用：持久化云同步的账号、设备标识、游标与最近一次错误。
 */
package com.banlimusic.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

private val Context.syncStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

data class SyncPersistentState(
    val deviceId: String = "",
    val accountName: String? = null,
    val lastSyncAt: Long = 0L,
    val lastEtag: String? = null,
    val lastError: String? = null,
)

/**
 * Kept out of the main settings DataStore: a sync updates these values often and
 * the app already collects AppSettings at the root, which would recompose every
 * screen on each sync tick.
 */
class SyncStateStore(private val context: Context) {

    val state: Flow<SyncPersistentState> = context.syncStateDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            SyncPersistentState(
                deviceId = preferences[Keys.deviceId].orEmpty(),
                accountName = preferences[Keys.accountName]?.takeIf { it.isNotBlank() },
                lastSyncAt = preferences[Keys.lastSyncAt] ?: 0L,
                lastEtag = preferences[Keys.lastEtag]?.takeIf { it.isNotBlank() },
                lastError = preferences[Keys.lastError]?.takeIf { it.isNotBlank() },
            )
        }

    suspend fun current(): SyncPersistentState = state.first()

    suspend fun ensureDeviceId(): String {
        val existing = current().deviceId
        if (existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.syncStateDataStore.edit { it[Keys.deviceId] = generated }
        return generated
    }

    suspend fun setAccount(name: String?) {
        context.syncStateDataStore.edit { preferences ->
            if (name.isNullOrBlank()) {
                preferences.remove(Keys.accountName)
            } else {
                preferences[Keys.accountName] = name
            }
        }
    }

    suspend fun setLastSync(at: Long, etag: String?) {
        context.syncStateDataStore.edit { preferences ->
            preferences[Keys.lastSyncAt] = at
            if (etag.isNullOrBlank()) {
                preferences.remove(Keys.lastEtag)
            } else {
                preferences[Keys.lastEtag] = etag
            }
        }
    }

    suspend fun setError(message: String?) {
        context.syncStateDataStore.edit { preferences ->
            if (message.isNullOrBlank()) {
                preferences.remove(Keys.lastError)
            } else {
                preferences[Keys.lastError] = message
            }
        }
    }

    suspend fun clearAccount() {
        context.syncStateDataStore.edit { preferences ->
            preferences.remove(Keys.accountName)
            preferences.remove(Keys.lastSyncAt)
            preferences.remove(Keys.lastEtag)
            preferences.remove(Keys.lastError)
        }
    }

    private object Keys {
        val deviceId = stringPreferencesKey("device_id")
        val accountName = stringPreferencesKey("account_name")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastEtag = stringPreferencesKey("last_etag")
        val lastError = stringPreferencesKey("last_error")
    }
}
