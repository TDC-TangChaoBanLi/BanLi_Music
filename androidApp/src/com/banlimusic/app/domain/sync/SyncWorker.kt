/**
 * 文件作用：在后台执行 OneDrive 收藏同步。
 */
package com.banlimusic.app.domain.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.data.sync.SyncStatus

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? MusicApplication ?: return Result.failure()
        val repository = application.container.oneDriveSyncRepository
        if (!repository.isConfigured() || !repository.isSignedIn()) return Result.success()
        return when (repository.syncNow()) {
            is SyncStatus.Success -> Result.success()
            is SyncStatus.Error -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "onedrive-sync-periodic"
        private const val MAX_RETRIES = 3
    }
}
