/**
 * 文件作用：使用 RemoteViews 渲染大小两种桌面小组件。
 */
package com.banlimusic.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.banlimusic.app.MainActivity
import com.banlimusic.app.R

/**
 * RemoteViews renderer. Frequent player ticks use [AppWidgetManager
 * .partiallyUpdateAppWidget] to update only text, icon and progress; artwork,
 * background, theme colors and click targets are refreshed in full updates.
 */
object WidgetRenderer {

    fun updateAll(context: Context, full: Boolean) {
        val state = PlayerWidgetUpdater.read(context)
        val manager = AppWidgetManager.getInstance(context)
        renderComponent(
            context = context,
            manager = manager,
            receiverClass = PlayerWidgetReceiver::class.java,
            layoutRes = R.layout.widget_player_small,
            isLarge = false,
            state = state,
            full = full,
        )
        renderComponent(
            context = context,
            manager = manager,
            receiverClass = PlayerWidgetLargeReceiver::class.java,
            layoutRes = R.layout.widget_player_large,
            isLarge = true,
            state = state,
            full = full,
        )
    }

    private fun renderComponent(
        context: Context,
        manager: AppWidgetManager,
        receiverClass: Class<*>,
        layoutRes: Int,
        isLarge: Boolean,
        state: WidgetState,
        full: Boolean,
    ) {
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, receiverClass))
        }.getOrDefault(IntArray(0))
        if (ids.isEmpty()) return
        val titleColor = if (state.dark) 0xFFF5F5F5.toInt() else 0xFF111111.toInt()
        val artistColor = if (state.dark) 0xFFBFBFBF.toInt() else 0xFF666666.toInt()
        val playIcon = when {
            state.isPlaying && state.dark -> R.drawable.ic_pause
            state.isPlaying -> R.drawable.ic_pause_dark
            state.dark -> R.drawable.ic_play
            else -> R.drawable.ic_play_dark
        }
        val previousIcon = if (state.dark) {
            R.drawable.ic_skip_previous
        } else {
            R.drawable.ic_skip_previous_dark
        }
        val nextIcon = if (state.dark) R.drawable.ic_skip_next else R.drawable.ic_skip_next_dark
        val favoriteIcon = when {
            state.isFavorite && state.dark -> R.drawable.ic_favorite
            state.isFavorite -> R.drawable.ic_favorite_dark
            state.dark -> R.drawable.ic_favorite_border
            else -> R.drawable.ic_favorite_border_dark
        }
        val progress = if (state.durationMs > 0L) {
            ((state.positionMs.toDouble() / state.durationMs) * 1000L)
                .toInt()
                .coerceIn(0, 1000)
        } else {
            0
        }

        fun RemoteViews.applyCommon() {
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            setTextColor(R.id.widget_title, titleColor)
            setTextColor(R.id.widget_artist, artistColor)
            setImageViewResource(R.id.widget_play, playIcon)
            setProgressBar(R.id.widget_progress, 1000, progress, false)
            if (isLarge) {
                setImageViewResource(R.id.widget_favorite, favoriteIcon)
            }
        }

        ids.forEach { appWidgetId ->
            if (full) {
                val views = RemoteViews(context.packageName, layoutRes).apply {
                    applyCommon()
                    setInt(
                        R.id.widget_root,
                        "setBackgroundResource",
                        if (state.dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light,
                    )
                    setImageViewResource(R.id.widget_previous, previousIcon)
                    setImageViewResource(R.id.widget_next, nextIcon)
                    setOnClickPendingIntent(
                        R.id.widget_root,
                        pendingIntent(context, REQUEST_OPEN, openAppIntent(context, state.hasMedia)),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_previous,
                        actionPending(context, REQUEST_PREVIOUS, WidgetActionActivity.ACTION_PREVIOUS),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_play,
                        actionPending(context, REQUEST_PLAY, WidgetActionActivity.ACTION_TOGGLE_PLAY),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_next,
                        actionPending(context, REQUEST_NEXT, WidgetActionActivity.ACTION_NEXT),
                    )
                    if (isLarge) {
                        setOnClickPendingIntent(
                            R.id.widget_favorite,
                            actionPending(
                                context,
                                REQUEST_FAVORITE,
                                WidgetActionActivity.ACTION_TOGGLE_FAVORITE,
                            ),
                        )
                        val artwork = runCatching {
                            state.artworkUrl?.let { WidgetArtworkStore.loadCached(context, it) }
                        }.getOrNull()
                        if (artwork != null) {
                            setImageViewBitmap(R.id.widget_artwork, artwork)
                        } else {
                            setImageViewResource(
                                R.id.widget_artwork,
                                R.drawable.ic_launcher_foreground,
                            )
                        }
                        if (artwork == null && !state.artworkUrl.isNullOrBlank()) {
                            WidgetArtworkStore.request(context, state.artworkUrl)
                        }
                    }
                }
                manager.updateAppWidget(appWidgetId, views)
            }
            // Always follow a full update with the tiny partial payload: text,
            // icon and progress are applied without re-inflating the layout, so
            // track changes do not wait for the launcher to replace RemoteViews.
            manager.partiallyUpdateAppWidget(
                appWidgetId,
                RemoteViews(context.packageName, layoutRes).apply { applyCommon() },
            )
        }
    }

    private fun actionPending(context: Context, requestCode: Int, action: String): PendingIntent =
        pendingIntent(
            context,
            requestCode,
            WidgetActionActivity.intent(context, action),
        )

    private fun pendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context, hasMedia: Boolean): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_FROM_WIDGET, true)
            putExtra(MainActivity.EXTRA_WIDGET_OPEN_PLAYER, hasMedia)
        }

    private const val REQUEST_OPEN = 10
    private const val REQUEST_PREVIOUS = 11
    private const val REQUEST_PLAY = 12
    private const val REQUEST_NEXT = 13
    private const val REQUEST_FAVORITE = 14
}
