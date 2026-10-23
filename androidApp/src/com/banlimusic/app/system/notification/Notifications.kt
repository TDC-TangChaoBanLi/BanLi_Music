package com.banlimusic.app.system.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.banlimusic.app.R
import com.banlimusic.app.MainActivity

object Notifications {
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_PLAYBACK = "playback_errors"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val downloads = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "下载任务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示音乐下载进度"
            setShowBadge(false)
        }
        val playback = NotificationChannel(
            CHANNEL_PLAYBACK,
            "播放错误",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "连续播放失败时提醒并停止播放"
        }
        manager.createNotificationChannels(listOf(downloads, playback))
    }

    fun notifyPlaybackStopped(context: Context, title: String, detail: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { manager.notify(PLAYBACK_ERROR_NOTIFICATION_ID, notification) }
    }

    fun downloadNotification(
        context: Context,
        title: String,
        detail: String,
        progressPercent: Int?,
        indeterminate: Boolean,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (indeterminate || progressPercent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progressPercent, false)
        }
        return builder.build()
    }

    fun downloadNotificationId(taskId: String): Int =
        20_000 + (taskId.hashCode() and 0x7FFF) % 8_000

    private const val PLAYBACK_ERROR_NOTIFICATION_ID = 30_001
}
