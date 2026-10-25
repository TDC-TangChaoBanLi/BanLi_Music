/**
 * 文件作用：接收小组件按钮点击并通过 MediaController 执行播放操作。
 */
package com.banlimusic.app.widget

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.banlimusic.app.player.FavoriteCommand
import com.banlimusic.app.player.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Invisible trampoline used by widget buttons. Some launchers do not deliver
 * broadcast-based widget actions reliably, while Activity PendingIntents are
 * handled like a normal widget click. The controller is created here, the action
 * is applied, and the activity finishes immediately.
 */
class WidgetActionActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val action = intent?.action
        if (action == null) {
            finish()
            return
        }
        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener(
            {
                runCatching {
                    controllerFuture.get(1, TimeUnit.SECONDS)
                }.getOrNull()?.let { mediaController ->
                    controller = mediaController
                    mediaController.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            syncWidget(mediaController)
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            syncWidget(mediaController)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            syncWidget(mediaController)
                        }
                    })
                    perform(action, mediaController)
                    scope.launch {
                        // Give the player/session a moment to apply the command,
                        // then write the new title/icon/progress immediately.
                        delay(350L)
                        runCatching {
                            PlayerWidgetUpdater.syncFromController(
                                this@WidgetActionActivity,
                                mediaController,
                            )
                        }
                        finish()
                    }
                } ?: finish()
            },
            mainHandler::post,
        )
    }

    private fun syncWidget(mediaController: MediaController) {
        scope.launch {
            runCatching {
                PlayerWidgetUpdater.syncFromController(this@WidgetActionActivity, mediaController)
            }
        }
    }

    private fun perform(action: String, mediaController: MediaController) {
        when (action) {
            ACTION_TOGGLE_PLAY -> when {
                mediaController.playbackState == Player.STATE_IDLE &&
                    mediaController.mediaItemCount > 0 -> {
                    mediaController.prepare()
                    mediaController.play()
                }

                mediaController.playWhenReady -> mediaController.pause()
                else -> mediaController.play()
            }

            ACTION_NEXT -> if (mediaController.hasNextMediaItem()) {
                mediaController.seekToNextMediaItem()
            }

            ACTION_PREVIOUS -> if (mediaController.currentPosition > 3_000L) {
                mediaController.seekTo(0L)
            } else if (mediaController.hasPreviousMediaItem()) {
                mediaController.seekToPreviousMediaItem()
            }

            ACTION_TOGGLE_FAVORITE -> mediaController.sendCustomCommand(
                FavoriteCommand.sessionCommand,
                Bundle.EMPTY,
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun finish() {
        controller?.release()
        controller = null
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val PREFIX = "com.banlimusic.app.widget.action."
        const val ACTION_TOGGLE_PLAY = "${PREFIX}TOGGLE_PLAY"
        const val ACTION_NEXT = "${PREFIX}NEXT"
        const val ACTION_PREVIOUS = "${PREFIX}PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "${PREFIX}TOGGLE_FAVORITE"

        fun intent(context: Context, action: String): Intent =
            Intent(context, WidgetActionActivity::class.java).setAction(action)
    }
}
