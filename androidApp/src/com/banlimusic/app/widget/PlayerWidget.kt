package com.banlimusic.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.banlimusic.app.R

class PlayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PlayerWidgetUpdater.PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(PlayerWidgetUpdater.KEY_TITLE, "BanLiMusic") ?: "BanLiMusic"
        val artist = prefs.getString(PlayerWidgetUpdater.KEY_ARTIST, "还没有播放歌曲")
            ?: "还没有播放歌曲"
        val isPlaying = prefs.getBoolean(PlayerWidgetUpdater.KEY_IS_PLAYING, false)

        provideContent {
            WidgetContent(title = title, artist = artist, isPlaying = isPlaying)
        }
    }
}

@Composable
private fun WidgetContent(title: String, artist: String, isPlaying: Boolean) {
    val background = ColorProvider(Color(0xFF0F4C45))
    val foreground = ColorProvider(Color(0xFFF2F7F5))
    val muted = ColorProvider(Color(0xFFBFD8D2))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = foreground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = artist,
            maxLines = 1,
            style = TextStyle(color = muted, fontSize = 13.sp),
            modifier = GlanceModifier.padding(top = 2.dp),
        )
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_previous),
                contentDescription = "上一首",
                modifier = GlanceModifier
                    .size(38.dp)
                    .clickable(actionRunCallback<PreviousTrackAction>()),
            )
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = GlanceModifier
                    .size(44.dp)
                    .padding(horizontal = 8.dp)
                    .clickable(actionRunCallback<TogglePlaybackAction>()),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_skip_next),
                contentDescription = "下一首",
                modifier = GlanceModifier
                    .size(38.dp)
                    .clickable(actionRunCallback<NextTrackAction>()),
            )
        }
    }
}

object PlayerWidgetUpdater {
    const val PREFS = "player_widget_state"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_IS_PLAYING = "is_playing"

    suspend fun update(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        hasMedia: Boolean,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_TITLE, if (hasMedia) title else "BanLiMusic")
            putString(KEY_ARTIST, if (hasMedia) artist else "还没有播放歌曲")
            putBoolean(KEY_IS_PLAYING, isPlaying)
        }
        runCatching { PlayerWidget().updateAll(context) }
    }
}
