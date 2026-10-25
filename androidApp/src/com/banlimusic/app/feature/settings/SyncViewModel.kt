/**
 * 文件作用：维护云同步页面的登录状态、同步进度与设置操作。
 */
package com.banlimusic.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.datastore.MAX_SYNC_INTERVAL_HOURS
import com.banlimusic.app.core.datastore.MIN_SYNC_INTERVAL_HOURS
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.sync.OneDriveConfig
import com.banlimusic.app.data.sync.AuthState
import com.banlimusic.app.data.sync.OneDriveSyncRepository
import com.banlimusic.app.data.sync.SyncStatus
import com.banlimusic.app.data.sync.syncUserMessage
import com.banlimusic.app.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val configured: Boolean = false,
    val auth: AuthState = AuthState.NOT_CONFIGURED,
    val accountName: String? = null,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
    val autoSync: Boolean = false,
    val intervalHours: Int = 6,
    val status: SyncStatus = SyncStatus.Idle,
    val redirectUri: String = "",
    val signatureHash: String = "",
    val signatureSha1: String = "",
)

class SyncViewModel(
    private val sync: OneDriveSyncRepository,
    private val settings: SettingsRepository,
    private val scheduler: SyncScheduler,
    signatureHash: String,
    private val signatureSha1: String,
) : ViewModel() {

    private val redirectUri = OneDriveConfig.redirectUri(signatureHash)

    val state: StateFlow<SyncUiState> = combine(
        settings.settings,
        sync.authState,
        sync.persistentState,
        sync.status,
    ) { appSettings, auth, persistent, status ->
        SyncUiState(
            configured = sync.isConfigured(),
            auth = auth,
            accountName = persistent.accountName,
            lastSyncAt = persistent.lastSyncAt,
            lastError = persistent.lastError,
            autoSync = appSettings.sync.autoSync,
            intervalHours = appSettings.sync.intervalHours,
            status = status,
            redirectUri = redirectUri,
            signatureHash = signatureHash,
            signatureSha1 = signatureSha1,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SyncUiState(
            configured = sync.isConfigured(),
            auth = sync.authState.value,
            redirectUri = redirectUri,
            signatureHash = signatureHash,
            signatureSha1 = signatureSha1,
        ),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun signIn() {
        val result = sync.signIn()
        result.exceptionOrNull()?.let { _message.value = it.syncUserMessage() }
    }

    fun signOut(deleteCloudFile: Boolean) {
        viewModelScope.launch {
            sync.signOut(deleteCloudFile)
            _message.value = if (deleteCloudFile) {
                "已断开 OneDrive，并尝试删除云端同步文件"
            } else {
                "已断开 OneDrive"
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            when (val status = sync.syncNow()) {
                is SyncStatus.Success -> {
                    _message.value = buildString {
                        append("同步完成")
                        if (status.addedMemberships > 0) append("，新增 ${status.addedMemberships} 项")
                        if (status.removedMemberships > 0) append("，移除 ${status.removedMemberships} 项")
                        if (status.addedFolders > 0) append("，新增 ${status.addedFolders} 个收藏夹")
                    }
                }

                is SyncStatus.Error -> _message.value = status.message
                else -> Unit
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settings.setSyncAutoSync(enabled)
            scheduler.apply(settings.current().sync)
        }
    }

    fun setIntervalHours(hours: Int) {
        viewModelScope.launch {
            settings.setSyncIntervalHours(hours.coerceIn(MIN_SYNC_INTERVAL_HOURS, MAX_SYNC_INTERVAL_HOURS))
            scheduler.apply(settings.current().sync)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
