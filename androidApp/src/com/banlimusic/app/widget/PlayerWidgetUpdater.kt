package com.banlimusic.app.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.player.TrackMediaItems
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WidgetState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasMedia: Boolean,
    val isFavorite: Boolean,
    val artworkUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val dark: Boolean,
)

/**
 * Owns the widget state snapshot and drives the RemoteViews renderer. The state
 * is deliberately persisted so a widget can redraw after the playback service is
 * recreated.
 */
object PlayerWidgetUpdater {
    const val PREFS = "player_widget_state"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_HAS_MEDIA = "has_media"
    const val KEY_IS_FAVORITE = "is_favorite"
    const val KEY_ARTWORK_URL = "artwork_url"
    const val KEY_POSITION_MS = "position_ms"
    const val KEY_POSITION_AT = "position_at_elapsed"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_WIDGET_THEME = "widget_theme"

    private val renderMutex = Mutex()

    fun read(context: Context): WidgetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_WIDGET_THEME, null)
            ?.let { runCatching { WidgetTheme.valueOf(it) }.getOrNull() }
            ?: WidgetTheme.SYSTEM
        val dark = when (theme) {
            WidgetTheme.BLACK -> true
            WidgetTheme.WHITE -> false
            WidgetTheme.SYSTEM -> {
                val mode = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        val duration = prefs.getLong(KEY_DURATION_MS, 0L)
        val sampledPosition = prefs.getLong(KEY_POSITION_MS, 0L)
        val sampledAt = prefs.getLong(KEY_POSITION_AT, 0L)
        val elapsed = if (isPlaying && sampledAt > 0L) {
            (SystemClock.elapsedRealtime() - sampledAt).coerceAtLeast(0L)
        } else {
            0L
        }
        val position = (sampledPosition + elapsed).let { value ->
            if (duration > 0L) value.coerceAtMost(duration) else value
        }
        return WidgetState(
            title = prefs.getString(KEY_TITLE, "BanLiMusic") ?: "BanLiMusic",
            artist = prefs.getString(KEY_ARTIST, "还没有播放歌曲") ?: "还没有播放歌曲",
            isPlaying = isPlaying,
            hasMedia = prefs.getBoolean(KEY_HAS_MEDIA, false),
            isFavorite = prefs.getBoolean(KEY_IS_FAVORITE, false),
            artworkUrl = prefs.getString(KEY_ARTWORK_URL, null),
            positionMs = position,
            durationMs = duration,
            dark = dark,
        )
    }

    suspend fun update(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        hasMedia: Boolean,
        isFavorite: Boolean = false,
        artworkUrl: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldTitle = prefs.getString(KEY_TITLE, "BanLiMusic")
        val oldArtwork = prefs.getString(KEY_ARTWORK_URL, null)
        val oldHasMedia = prefs.getBoolean(KEY_HAS_MEDIA, false)
        val full = title != oldTitle || artworkUrl != oldArtwork || hasMedia != oldHasMedia
        prefs.edit {
            putString(KEY_TITLE, if (hasMedia) title else "BanLiMusic")
            putString(KEY_ARTIST, if (hasMedia) artist else "还没有播放歌曲")
            putBoolean(KEY_IS_PLAYING, isPlaying)
            putBoolean(KEY_HAS_MEDIA, hasMedia)
            putBoolean(KEY_IS_FAVORITE, isFavorite)
            putString(KEY_ARTWORK_URL, artworkUrl)
            putLong(KEY_POSITION_MS, positionMs)
            putLong(KEY_POSITION_AT, SystemClock.elapsedRealtime())
            putLong(KEY_DURATION_MS, durationMs)
        }
        render(context, full)
    }

    suspend fun refresh(context: Context) {
        render(context, full = true)
    }

    suspend fun setTheme(context: Context, theme: WidgetTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_WIDGET_THEME, theme.name)
        }
        render(context, full = true)
    }

    /**
     * Reads the authoritative player state right after a widget action, before
     * the media service's asynchronous events reach the widget state store.
     */
    suspend fun syncFromController(context: Context, controller: MediaController) {
        val app = context.applicationContext as? MusicApplication
        val item = controller.currentMediaItem
        val track = runCatching {
            item?.let { TrackMediaItems.trackOf(it, app?.container?.json ?: return) }
        }.getOrNull()
        val metadata = item?.mediaMetadata
        val title = metadata?.title?.toString() ?: track?.title ?: "BanLiMusic"
        val artist = metadata?.artist?.toString() ?: track?.artistLine ?: "还没有播放歌曲"
        val artworkUrl = metadata?.artworkUri?.toString() ?: track?.artworkUrl
        val duration = when {
            controller.duration > 0L && controller.duration != C.TIME_UNSET -> controller.duration
            (track?.durationMs ?: 0L) > 0L -> track?.durationMs ?: 0L
            else -> 0L
        }
        val favorite = item?.mediaId?.let { id ->
            runCatching { app?.container?.libraryRepository?.isFavorite(id) }.getOrNull()
        } ?: false
        update(
            context = context,
            title = title,
            artist = artist,
            isPlaying = controller.playWhenReady && controller.playbackState != Player.STATE_ENDED,
            hasMedia = item != null,
            isFavorite = favorite,
            artworkUrl = artworkUrl,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
        )
    }

    private suspend fun render(context: Context, full: Boolean) = renderMutex.withLock {
        runCatching { WidgetRenderer.updateAll(context, full) }
    }
}
