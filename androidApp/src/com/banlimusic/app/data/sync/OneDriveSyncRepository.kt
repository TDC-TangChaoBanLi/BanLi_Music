/**
 * 文件作用：协调收藏数据的本地快照、OneDrive 拉取合并与上传。
 */
package com.banlimusic.app.data.sync

import android.net.Uri
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.datastore.SyncPersistentState
import com.banlimusic.app.core.datastore.SyncStateStore
import com.banlimusic.app.core.model.FavoriteSyncDocument
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.domain.sync.FavoriteMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    data class Success(
        val addedMemberships: Int,
        val removedMemberships: Int,
        val addedFolders: Int,
        val at: Long,
    ) : SyncStatus

    data class Error(val message: String, val at: Long) : SyncStatus
}

/**
 * Single entry point for cloud sync. All network and database work is serialized
 * so a manual tap and a WorkManager run can never interleave their read-modify-write.
 */
class OneDriveSyncRepository(
    private val library: LibraryRepository,
    private val client: OneDriveClient,
    private val auth: OneDriveAuthManager,
    private val stateStore: SyncStateStore,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val authState: StateFlow<AuthState> = auth.state
    val persistentState: kotlinx.coroutines.flow.Flow<SyncPersistentState> = stateStore.state

    fun isConfigured(): Boolean = auth.isConfigured

    fun isSignedIn(): Boolean = auth.isSignedIn()

    fun signIn(): Result<Unit> = auth.signIn()

    suspend fun completeSignIn(uri: Uri): Result<Unit> {
        val result = auth.completeSignIn(uri)
        if (result.isSuccess) {
            refreshAccount()
            // First sync right after login so a new device immediately receives
            // the library instead of waiting for the periodic worker.
            syncNow()
        }
        return result
    }

    suspend fun signOut(deleteCloudFile: Boolean) {
        if (deleteCloudFile && auth.isSignedIn()) {
            runCatching { client.deleteLibrary() }
        }
        auth.signOut()
        stateStore.clearAccount()
        _status.value = SyncStatus.Idle
    }

    suspend fun refreshAccount() {
        if (!auth.isSignedIn()) return
        val account = runCatching { client.account() }.getOrNull() ?: return
        stateStore.setAccount(account.displayName ?: account.accountName)
    }

    /**
     * Pull → merge → apply locally → push. A concurrent edit from another device
     * surfaces as HTTP 412 and is resolved by pulling and merging again.
     */
    suspend fun syncNow(): SyncStatus = mutex.withLock {
        if (!auth.isConfigured) {
            return@withLock recordError("尚未配置 OneDrive 应用 ID")
        }
        if (!auth.isSignedIn()) {
            return@withLock recordError("请先登录 OneDrive")
        }
        _status.value = SyncStatus.Syncing
        val deviceId = stateStore.ensureDeviceId()
        if (stateStore.current().accountName.isNullOrBlank()) refreshAccount()

        try {
            // Pull first so local edits made while the network request is in
            // flight are included in the snapshot below.
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
            var synced = false
            while (!synced) {
                try {
                    val newEtag = client.uploadLibrary(merged.document, etag)
                    val finishedAt = System.currentTimeMillis()
                    stateStore.setLastSync(finishedAt, newEtag)
                    stateStore.setError(null)
                    val success = SyncStatus.Success(
                        addedMemberships = merged.addedMemberships,
                        removedMemberships = merged.removedMemberships,
                        addedFolders = merged.addedFolders,
                        at = finishedAt,
                    )
                    _status.value = success
                    return@withLock success
                } catch (e: SyncException.Conflict) {
                    attempts++
                    if (attempts >= MAX_UPLOAD_ATTEMPTS) throw e
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
            // Unreachable: the loop either returns or throws.
            error("sync loop exited without a result")
        } catch (e: CancellationException) {
            _status.value = SyncStatus.Idle
            throw e
        } catch (e: Exception) {
            val message = e.syncUserMessage()
            stateStore.setError(message)
            val error = SyncStatus.Error(message, System.currentTimeMillis())
            _status.value = error
            error
        }
    }

    /** Startup sync is throttled so cold starts do not hammer OneDrive. */
    suspend fun maybeSyncOnStart() {
        val preferences = settings.current()
        if (!preferences.sync.autoSync || !auth.isSignedIn()) return
        val last = stateStore.current().lastSyncAt
        if (System.currentTimeMillis() - last < STARTUP_SYNC_THRESHOLD_MS) return
        syncNow()
    }

    private fun recordError(message: String): SyncStatus {
        val error = SyncStatus.Error(message, System.currentTimeMillis())
        _status.value = error
        return error
    }

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 3
        const val STARTUP_SYNC_THRESHOLD_MS = 30L * 60L * 1_000L
    }
}
