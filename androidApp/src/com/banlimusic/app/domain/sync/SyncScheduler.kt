/**
 * 文件作用：安排 OneDrive 同步的周期任务与手动立即同步任务。
 */
package com.banlimusic.app.domain.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.banlimusic.app.core.datastore.MAX_SYNC_INTERVAL_HOURS
import com.banlimusic.app.core.datastore.MIN_SYNC_INTERVAL_HOURS
import com.banlimusic.app.core.datastore.SyncSettings
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context) {

    private val manager: WorkManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Applies the user's preference; safe to call on every cold start. */
    fun apply(settings: SyncSettings) {
        if (!settings.autoSync) {
            manager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC)
            return
        }
        val hours = settings.intervalHours
            .coerceIn(MIN_SYNC_INTERVAL_HOURS, MAX_SYNC_INTERVAL_HOURS)
            .toLong()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
