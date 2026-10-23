package com.banlimusic.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.banlimusic.app.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

data class PlaybackUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrack: Track? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val errorMessage: String? = null,
)

/**
 * Application-wide connection to the Media3 session. The single source of truth
 * for playback state is the session/player; every screen and the widget observe it.
 */
class PlaybackController(
    private val context: Context,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _state.update { it.copy(errorMessage = null) }
            updateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    errorMessage = error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "播放失败，请尝试其他歌曲或来源",
                )
            }
        }
    }

    init {
        connect()
        ticker = scope.launch {
            while (isActive) {
                val player = controller
                if (player != null) {
                    updatePosition(player)
                }
                delay(500L)
            }
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val result = runCatching { future.get() }
                result.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    _state.update { it.copy(connected = true) }
                    updateState(mediaController)
                    pendingAction?.let { action ->
                        pendingAction = null
                        action(mediaController)
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            connected = false,
                            errorMessage = "无法连接播放服务：${error.message.orEmpty()}",
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(track: Track, queue: List<Track> = emptyList()) {
        val items = (queue.ifEmpty { listOf(track) }).map { TrackMediaItems.create(it, json) }
        val index = items.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
        withController { player ->
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playFile(track: Track, file: File) {
        val item = TrackMediaItems.createLocal(track, Uri.fromFile(file), json)
        withController { player ->
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        withController { player ->
            when {
                player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0 -> {
                    player.prepare()
                    player.play()
                }
                player.isPlaying -> player.pause()
                else -> player.play()
            }
        }
    }

    fun next() {
        withController { player -> if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    }

    fun previous() {
        withController { player ->
            if (player.currentPosition > 3_000L) player.seekTo(0L)
            else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        withController { player -> player.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun stop() {
        withController { player ->
            player.stop()
            player.clearMediaItems()
        }
    }

    fun cycleRepeatMode() {
        withController { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleShuffle() {
        withController { player -> player.shuffleModeEnabled = !player.shuffleModeEnabled }
    }

    /** Updates the notification/player artwork after the URL has been resolved lazily. */
    fun updateArtwork(trackId: String, artworkUrl: String) {
        withController { player ->
            val index = player.currentMediaItemIndex
            val item = player.currentMediaItem ?: return@withController
            if (item.mediaId != trackId) return@withController
            if (item.mediaMetadata.artworkUri?.toString() == artworkUrl) return@withController
            runCatching { player.replaceMediaItem(index, TrackMediaItems.withArtwork(item, artworkUrl)) }
            _state.update { it.copy(artworkUrl = artworkUrl) }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    private fun withController(action: (MediaController) -> Unit) {
        val player = controller
        if (player != null) {
            action(player)
            updateState(player)
        } else {
            pendingAction = action
            connect()
        }
    }

    private fun updateState(player: Player? = controller) {
        val current = player ?: return
        val item = current.currentMediaItem
        val track = TrackMediaItems.trackOf(item, json)
        _state.update { previous ->
            previous.copy(
                connected = true,
                isPlaying = current.isPlaying,
                isBuffering = current.playbackState == Player.STATE_BUFFERING,
                currentTrack = track,
                title = item?.mediaMetadata?.title?.toString(),
                artist = item?.mediaMetadata?.artist?.toString(),
                artworkUrl = item?.mediaMetadata?.artworkUri?.toString() ?: track?.artworkUrl,
                positionMs = current.currentPosition.coerceAtLeast(0L),
                durationMs = effectiveDuration(current),
                hasNext = current.hasNextMediaItem(),
                hasPrevious = current.hasPreviousMediaItem(),
                shuffleEnabled = current.shuffleModeEnabled,
                repeatMode = current.repeatMode,
            )
        }
    }

    private fun updatePosition(player: MediaController) {
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = effectiveDuration(player),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
            )
        }
    }

    private fun effectiveDuration(player: Player): Long {
        val duration = player.duration
        if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) return duration
        val track = TrackMediaItems.trackOf(player.currentMediaItem, json)
        return track?.durationMs ?: 0L
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
