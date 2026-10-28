package com.banlimusic.desktop.data.sync

import com.banlimusic.app.core.datastore.SyncSettings
import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.data.sync.OneDriveClient
import com.banlimusic.app.data.sync.SyncException
import com.banlimusic.app.data.sync.syncUserMessage
import com.banlimusic.app.domain.sync.FavoriteMerge
import com.banlimusic.desktop.data.DesktopLibraryRepository
import com.banlimusic.desktop.data.DesktopSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface DesktopSyncStatus {
    data object Idle : DesktopSyncStatus
    data object Syncing : DesktopSyncStatus
    data class Success(
        val addedMemberships: Int,
        val removedMemberships: Int,
        val addedFolders: Int,
        val at: Long,
    ) : DesktopSyncStatus
    data class Error(val message: String, val at: Long) : DesktopSyncStatus
}

/** Desktop counterpart of the Android sync coordinator, sharing merge/client code. */
class DesktopOneDriveSyncRepository(
    private val library: DesktopLibraryRepository,
    private val client: OneDriveClient,
    private val auth: DesktopOneDriveAuthManager,
    private val stateStore: DesktopSyncStateStore,
    private val settings: DesktopSettingsRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val _status = MutableStateFlow<DesktopSyncStatus>(DesktopSyncStatus.Idle)
    val status: StateFlow<DesktopSyncStatus> = _status.asStateFlow()

    val authState: StateFlow<DesktopAuthState> = auth.state
    val persistentState: StateFlow<DesktopSyncPersistentState> = stateStore.state

    fun isConfigured(): Boolean = auth.isConfigured
    fun isSignedIn(): Boolean = auth.isSignedIn()
    fun redirectUri(): String = auth.redirectUri()

    fun signIn(onResult: (Result<Unit>) -> Unit) {
        auth.signIn { result ->
            if (result.isSuccess) {
                scope.launch {
                    refreshAccount()
                    syncNow()
                }
            }
            onResult(result)
        }
    }

    fun signOut(deleteCloudFile: Boolean) {
        scope.launch {
            if (deleteCloudFile && auth.isSignedIn()) {
                runCatching { client.deleteLibrary() }
            }
            auth.signOut()
            stateStore.clearAccount()
            _status.value = DesktopSyncStatus.Idle
        }
    }

    suspend fun syncNow(): DesktopSyncStatus = mutex.withLock<DesktopSyncStatus> {
        if (!auth.isConfigured) {
            return@withLock recordError("尚未配置 OneDrive 应用 ID")
        }
        if (!auth.isSignedIn()) {
            return@withLock recordError("请先登录 OneDrive")
        }
        _status.value = DesktopSyncStatus.Syncing
        val deviceId = stateStore.ensureDeviceId()
        if (stateStore.current().accountName.isNullOrBlank()) refreshAccount()

        try {
            val firstRemote = client.downloadLibrary()
            var merged = FavoriteMerge.merge(
                local = library.exportSyncDocument(deviceId),
                remote = firstRemote.document ?: FavoriteSyncDocument(),
                now = System.currentTimeMillis(),
                deviceId = deviceId,
            )
            library.applySyncDocument(merged.document)

            var etag = firstRemote.etag
            var attempts = 0
            while (true) {
                try {
                    val newEtag = client.uploadLibrary(merged.document, etag)
                    val finishedAt = System.currentTimeMillis()
                    stateStore.setLastSync(finishedAt, newEtag)
                    stateStore.setError(null)
                    return@withLock DesktopSyncStatus.Success(
                        addedMemberships = merged.addedMemberships,
                        removedMemberships = merged.removedMemberships,
                        addedFolders = merged.addedFolders,
                        at = finishedAt,
                    ).also { _status.value = it }
                } catch (conflict: SyncException.Conflict) {
                    attempts++
                    if (attempts >= MAX_UPLOAD_ATTEMPTS) throw conflict
                    val latest = client.downloadLibrary()
                    merged = FavoriteMerge.merge(
                        local = merged.document,
                        remote = latest.document ?: FavoriteSyncDocument(),
                        now = System.currentTimeMillis(),
                        deviceId = deviceId,
                    )
                    library.applySyncDocument(merged.document)
                    etag = latest.etag
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("sync loop exited without a result")
        } catch (cancelled: CancellationException) {
            _status.value = DesktopSyncStatus.Idle
            throw cancelled
        } catch (error: Exception) {
            val message = error.syncUserMessage()
            stateStore.setError(message)
            val failure = DesktopSyncStatus.Error(message, System.currentTimeMillis())
            _status.value = failure
            failure
        }
    }

    /** Startup sync is throttled; desktop has no background scheduler yet. */
    suspend fun maybeSyncOnStart() {
        val preferences = settings.current()
        if (!preferences.sync.autoSync || !auth.isSignedIn()) return
        val last = stateStore.current().lastSyncAt
        if (System.currentTimeMillis() - last < STARTUP_SYNC_THRESHOLD_MS) return
        syncNow()
    }

    fun currentSyncSettings(): SyncSettings = settings.current().sync

    private suspend fun refreshAccount() {
        if (!auth.isSignedIn()) return
        val account = runCatching { client.account() }.getOrNull() ?: return
        stateStore.setAccount(account.displayName ?: account.accountName)
    }

    private fun recordError(message: String): DesktopSyncStatus {
        val error = DesktopSyncStatus.Error(message, System.currentTimeMillis())
        _status.value = error
        return error
    }

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 3
        const val STARTUP_SYNC_THRESHOLD_MS = 30L * 60L * 1_000L
    }
}
