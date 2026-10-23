package com.banlimusic.app.widget

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.R
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.ui.components.formatPlaybackTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

private data class WidgetState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasMedia: Boolean,
    val artworkUri: String?,
    val positionMs: Long,
    val durationMs: Long,
    val dark: Boolean,
)

class PlayerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideWidget(context, isLarge = false)
    }
}

class PlayerWidgetLarge : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideWidget(context, isLarge = true)
    }
}

private suspend fun GlanceAppWidget.provideWidget(context: Context, isLarge: Boolean) {
    val prefs = context.getSharedPreferences(PlayerWidgetUpdater.PREFS, Context.MODE_PRIVATE)
    val app = context.applicationContext as? MusicApplication
    val theme = runCatching {
        app?.container?.settingsRepository?.current()?.widgetTheme
    }.getOrNull() ?: WidgetTheme.SYSTEM
    val dark = when (theme) {
        WidgetTheme.BLACK -> true
        WidgetTheme.WHITE -> false
        WidgetTheme.SYSTEM -> {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mode == Configuration.UI_MODE_NIGHT_YES
        }
    }
    val state = WidgetState(
        title = prefs.getString(PlayerWidgetUpdater.KEY_TITLE, "BanLiMusic") ?: "BanLiMusic",
        artist = prefs.getString(PlayerWidgetUpdater.KEY_ARTIST, "还没有播放歌曲")
            ?: "还没有播放歌曲",
        isPlaying = prefs.getBoolean(PlayerWidgetUpdater.KEY_IS_PLAYING, false),
        hasMedia = prefs.getBoolean(PlayerWidgetUpdater.KEY_HAS_MEDIA, false),
        artworkUri = prefs.getString(PlayerWidgetUpdater.KEY_ARTWORK_URI, null),
        positionMs = prefs.getLong(PlayerWidgetUpdater.KEY_POSITION_MS, 0L),
        durationMs = prefs.getLong(PlayerWidgetUpdater.KEY_DURATION_MS, 0L),
        dark = dark,
    )
    provideContent {
        WidgetContent(state = state, isLarge = isLarge)
    }
}

@Composable
private fun WidgetContent(state: WidgetState, isLarge: Boolean) {
    val background = ColorProvider(Color(if (state.dark) 0xCC111111 else 0xCCF7F7F7))
    val foreground = ColorProvider(Color(if (state.dark) 0xFFF5F5F5 else 0xFF111111))
    val muted = ColorProvider(Color(if (state.dark) 0xFFBFBFBF else 0xFF666666))
    val accent = ColorProvider(Color(if (state.dark) 0xFF7DD3C7 else 0xFF0F766E))
    val track = ColorProvider(Color(if (state.dark) 0x33FFFFFF else 0x22000000))
    val fraction = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(22.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (isLarge) {
            if (!state.artworkUri.isNullOrBlank()) {
                Image(
                    provider = androidx.glance.appwidget.ImageProvider(Uri.parse(state.artworkUri)),
                    contentDescription = "专辑封面",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(60.dp)
                        .cornerRadius(12.dp),
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(60.dp)
                        .background(ColorProvider(Color(if (state.dark) 0x33FFFFFF else 0x22000000)))
                        .cornerRadius(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_launcher_foreground),
                        contentDescription = "BanLiMusic",
                        modifier = GlanceModifier.size(36.dp),
                    )
                }
            }
            Spacer(GlanceModifier.width(12.dp))
        }

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.title,
                maxLines = 1,
                style = TextStyle(
                    color = foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = state.artist,
                maxLines = 1,
                style = TextStyle(color = muted, fontSize = 12.sp),
                modifier = GlanceModifier.padding(top = 1.dp),
            )
            if (state.hasMedia) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(top = 7.dp),
                    color = accent,
                    backgroundColor = track,
                )
                Text(
                    text = "${formatPlaybackTime(state.positionMs)} / " +
                        formatPlaybackTime(state.durationMs),
                    maxLines = 1,
                    style = TextStyle(color = muted, fontSize = 10.sp),
                    modifier = GlanceModifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(GlanceModifier.width(8.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetButton(
                iconRes = R.drawable.ic_skip_previous,
                description = "上一首",
                action = actionRunCallback<PreviousTrackAction>(),
            )
            Spacer(GlanceModifier.width(2.dp))
            WidgetButton(
                iconRes = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                description = if (state.isPlaying) "暂停" else "播放",
                action = actionRunCallback<TogglePlaybackAction>(),
                size = 36.dp,
            )
            Spacer(GlanceModifier.width(2.dp))
            WidgetButton(
                iconRes = R.drawable.ic_skip_next,
                description = "下一首",
                action = actionRunCallback<NextTrackAction>(),
            )
        }
    }
}

@Composable
private fun WidgetButton(
    iconRes: Int,
    description: String,
    action: Action,
    size: androidx.compose.ui.unit.Dp = 32.dp,
) {
    Image(
        provider = ImageProvider(iconRes),
        contentDescription = description,
        modifier = GlanceModifier
            .size(size)
            .clickable(action),
    )
}

object PlayerWidgetUpdater {
    const val PREFS = "player_widget_state"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_HAS_MEDIA = "has_media"
    const val KEY_ARTWORK_URI = "artwork_uri"
    const val KEY_POSITION_MS = "position_ms"
    const val KEY_DURATION_MS = "duration_ms"

    suspend fun update(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        hasMedia: Boolean,
        artworkUri: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_TITLE, if (hasMedia) title else "BanLiMusic")
            putString(KEY_ARTIST, if (hasMedia) artist else "还没有播放歌曲")
            putBoolean(KEY_IS_PLAYING, isPlaying)
            putBoolean(KEY_HAS_MEDIA, hasMedia)
            putString(KEY_ARTWORK_URI, artworkUri)
            putLong(KEY_POSITION_MS, positionMs)
            putLong(KEY_DURATION_MS, durationMs)
        }
        runCatching { PlayerWidget().updateAll(context) }
        runCatching { PlayerWidgetLarge().updateAll(context) }
    }

    suspend fun refresh(context: Context) {
        runCatching { PlayerWidget().updateAll(context) }
        runCatching { PlayerWidgetLarge().updateAll(context) }
    }
}

object WidgetArtworkStore {
    private const val FILE_NAME = "widget_artwork.jpg"
    private const val MAX_AGE_MS = 6 * 60 * 60_000L

    suspend fun write(context: Context, artworkUrl: String): String? = withContext(Dispatchers.IO) {
        if (artworkUrl.isBlank()) return@withContext null
        val file = File(context.cacheDir, FILE_NAME)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
            return@withContext fileUri(context, file)
        }
        val app = context.applicationContext as? MusicApplication ?: return@withContext null
        val request = Request.Builder()
            .url(artworkUrl)
            .header("User-Agent", JsonHttpClient.USER_AGENT)
            .build()
        val success = runCatching {
            app.container.downloadHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                response.body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }
        }.getOrDefault(false)
        if (success) fileUri(context, file) else null
    }

    private fun fileUri(context: Context, file: File): String? = runCatching {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        ).toString()
    }.getOrNull()
}