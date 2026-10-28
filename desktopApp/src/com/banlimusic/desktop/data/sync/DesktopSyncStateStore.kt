package com.banlimusic.desktop.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.prefs.Preferences

data class DesktopSyncPersistentState(
    val deviceId: String = "",
    val accountName: String? = null,
    val lastSyncAt: Long = 0L,
    val lastEtag: String? = null,
    val lastError: String? = null,
)

/** Preferences-backed sync metadata for the desktop client. */
class DesktopSyncStateStore {

    private val prefs = Preferences.userRoot().node("com/banlimusic/desktop/sync")
    private val _state = MutableStateFlow(read())
    val state: StateFlow<DesktopSyncPersistentState> = _state.asStateFlow()

    fun current(): DesktopSyncPersistentState = _state.value

    fun ensureDeviceId(): String {
        val existing = _state.value.deviceId
        if (existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.put(KEY_DEVICE_ID, generated)
        flush()
        _state.value = read()
        return generated
    }

    fun setAccount(name: String?) {
        if (name.isNullOrBlank()) prefs.remove(KEY_ACCOUNT) else prefs.put(KEY_ACCOUNT, name)
        flush()
        _state.value = read()
    }

    fun setLastSync(at: Long, etag: String?) {
        prefs.putLong(KEY_LAST_SYNC, at)
        if (etag.isNullOrBlank()) prefs.remove(KEY_ETAG) else prefs.put(KEY_ETAG, etag)
        flush()
        _state.value = read()
    }

    fun setError(message: String?) {
        if (message.isNullOrBlank()) prefs.remove(KEY_ERROR) else prefs.put(KEY_ERROR, message)
        flush()
        _state.value = read()
    }

    fun clearAccount() {
        listOf(KEY_ACCOUNT, KEY_LAST_SYNC, KEY_ETAG, KEY_ERROR).forEach(prefs::remove)
        flush()
        _state.value = read()
    }

    private fun read(): DesktopSyncPersistentState = DesktopSyncPersistentState(
        deviceId = prefs.get(KEY_DEVICE_ID, ""),
        accountName = prefs.get(KEY_ACCOUNT, null)?.takeIf { it.isNotBlank() },
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        lastEtag = prefs.get(KEY_ETAG, null)?.takeIf { it.isNotBlank() },
        lastError = prefs.get(KEY_ERROR, null)?.takeIf { it.isNotBlank() },
    )

    private fun flush() {
        runCatching { prefs.flush() }
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ACCOUNT = "account_name"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_ETAG = "last_etag"
        const val KEY_ERROR = "last_error"
    }
}
