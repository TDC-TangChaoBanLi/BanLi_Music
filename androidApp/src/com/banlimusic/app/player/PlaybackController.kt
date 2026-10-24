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
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.system.notification.Notifications
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
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var ticker: Job? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var consecutiveFailures = 0
    private var failedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retryJob?.cancel()
            retryAttempt = 0
            failedMediaId = null
            _state.update { it.copy(errorMessage = null) }
            updateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                retryJob?.cancel()
                retryAttempt = 0
                consecutiveFailures = 0
                failedMediaId = null
            }
            updateState()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackFailure(error)
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
        retryJob?.cancel()
        retryAttempt = 0
        consecutiveFailures = 0
        failedMediaId = null
        scope.launch {
            val tracks = queue.ifEmpty { listOf(track) }
            val localUris = runCatching {
                downloads.completedUris(tracks.map { it.id })
            }.getOrDefault(emptyMap())
            val items = tracks.map { item ->
                val localUri = localUris[item.id]
                if (localUri != null) {
                    TrackMediaItems.createLocal(item, localUri, json)
                } else {
                    TrackMediaItems.create(item, json)
                }
            }
            val index = items.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
            withController { player ->
                player.setMediaItems(items, index, 0L)
                // The UI's "列表循环" mode must actually loop the whole queue.
                if (!player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_OFF) {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                player.prepare()
                player.play()
            }
        }
    }

    fun playFile(track: Track, file: File) = playFile(track, Uri.fromFile(file))

    fun playFile(track: Track, uri: Uri) {
        retryJob?.cancel()
        retryAttempt = 0
        consecutiveFailures = 0
        failedMediaId = null
        val item = TrackMediaItems.createLocal(track, uri, json)
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
        withController { player ->
            if (player.mediaItemCount == 0) return@withController
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                // List loop: wrap from the last track to the first.
                player.seekTo(0, 0L)
                player.play()
            }
        }
    }

    fun previous() {
        withController { player ->
            when {
                player.currentPosition > 3_000L -> player.seekTo(0L)
                player.hasPreviousMediaItem() -> player.seekToPreviousMediaItem()
                player.mediaItemCount > 0 -> {
                    // List loop: wrap from the first track to the last.
                    player.seekTo(player.mediaItemCount - 1, 0L)
                    player.play()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { player -> player.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun stop() {
        retryJob?.cancel()
        retryAttempt = 0
        consecutiveFailures = 0
        failedMediaId = null
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

    /**
     * Single-button play mode: shuffle → list repeat → single repeat → shuffle.
     * Keeping both flags in one cycle avoids the confusing two-button layout.
     */
    fun cyclePlayMode() {
        withController { player ->
            when {
                player.shuffleModeEnabled -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }

                player.repeatMode == Player.REPEAT_MODE_ONE -> {
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.shuffleModeEnabled = true
                }

                else -> player.repeatMode = Player.REPEAT_MODE_ONE
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

    private fun handlePlaybackFailure(error: PlaybackException) {
        val mediaId = controller?.currentMediaItem?.mediaId
        if (failedMediaId != mediaId) {
            failedMediaId = mediaId
            retryAttempt = 0
        }
        retryJob?.cancel()
        retryJob = scope.launch {
            val currentSettings = settings.current()
            val attempt = retryAttempt
            if (attempt < currentSettings.playbackRetryCount) {
                val delayMs = RETRY_BASE_DELAY_MS shl attempt
                _state.update {
                    it.copy(
                        errorMessage = "播放失败，${delayMs / 1_000} 秒后重试" +
                            "（${attempt + 1}/${currentSettings.playbackRetryCount}）",
                    )
                }
                delay(delayMs)
                retryAttempt = attempt + 1
                withController { player ->
                    if (player.currentMediaItem?.mediaId == mediaId) {
                        player.prepare()
                        player.play()
                    }
                }
            } else {
                retryAttempt = 0
                consecutiveFailures += 1
                if (consecutiveFailures >= currentSettings.consecutiveFailureLimit) {
                    _state.update {
                        it.copy(errorMessage = "连续 $consecutiveFailures 首播放失败，已停止播放")
                    }
                    Notifications.notifyPlaybackStopped(
                        context,
                        "播放已停止",
                        "已连续 $consecutiveFailures 首歌曲播放失败。请检查网络或切换音乐源。",
                    )
                    withController { player -> player.pause() }
                } else {
                    _state.update { it.copy(errorMessage = "播放失败，自动跳到下一首") }
                    withController { player ->
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.prepare()
                            player.play()
                        } else {
                            player.pause()
                            Notifications.notifyPlaybackStopped(
                                context,
                                "播放失败",
                                "已经是队列最后一首，播放已停止。",
                            )
                        }
                    }
                }
            }
        }
    }

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
        retryJob?.cancel()
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private companion object {
        const val RETRY_BASE_DELAY_MS = 2_000L
    }
}
