package com.banlimusic.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.banlimusic.app.player.MusicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private suspend fun withController(context: Context, action: (MediaController) -> Unit) {
    val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
    val future = withContext(Dispatchers.Main) {
        MediaController.Builder(context, token).buildAsync()
    }
    val controller = runCatching {
        withContext(Dispatchers.IO) { future.get(5, TimeUnit.SECONDS) }
    }.getOrNull() ?: return
    try {
        withContext(Dispatchers.Main) { action(controller) }
    } finally {
        withContext(Dispatchers.Main) { controller.release() }
    }
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withController(context) { controller ->
            when {
                controller.playbackState == androidx.media3.common.Player.STATE_IDLE &&
                    controller.mediaItemCount > 0 -> {
                    controller.prepare()
                    controller.play()
                }
                controller.isPlaying -> controller.pause()
                else -> controller.play()
            }
        }
    }
}

class NextTrackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withController(context) { controller ->
            if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
        }
    }
}

class PreviousTrackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withController(context) { controller ->
            if (controller.currentPosition > 3_000L) {
                controller.seekTo(0L)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            }
        }
    }
}
