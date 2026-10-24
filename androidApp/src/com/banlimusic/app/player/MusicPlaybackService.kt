package com.banlimusic.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.core.net.toUri
import com.banlimusic.app.MainActivity
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.domain.playback.StreamUriCodec
import com.banlimusic.app.widget.PlayerWidgetUpdater
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastRecordedMediaId: String? = null
    private var widgetTicker: Job? = null
    @Volatile
    private var favoriteIds: Set<String> = emptySet()
    @Volatile
    private var currentFavorite = false
    private val trackById = ConcurrentHashMap<String, Track>()

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = SessionCommands.Builder()
                .add(FavoriteCommand.sessionCommand)
                .build()
            val playerCommands = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == FavoriteCommand.ACTION) {
                toggleFavorite()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as MusicApplication).container

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(JsonHttpClient.USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == StreamUriCodec.SCHEME) {
                val uriText = uri.toString()
                val encoded = uri.lastPathSegment.orEmpty()
                val candidates = StreamUriCodec.decode(encoded)
                val resolved = container.streamResolver.resolveBlockingInfo(candidates)
                val mediaId = StreamUriCodec.mediaIdOf(uriText)
                val cacheKey = mediaId?.let {
                    StreamUriCodec.cacheKey(it, resolved.qualityKbps)
                }
                if (mediaId != null && cacheKey != null) {
                    trackById[mediaId]?.let { track ->
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                container.cacheRepository.record(
                                    track = track,
                                    cacheKey = cacheKey,
                                    qualityKbps = resolved.qualityKbps,
                                    totalBytes = resolved.info.sizeBytes ?: 0L,
                                    sourceUrl = resolved.info.url,
                                )
                            }
                        }
                    }
                }
                dataSpec.buildUpon()
                    .setUri(resolved.info.url.toUri())
                    .apply { if (cacheKey != null) setKey(cacheKey) }
                    .build()
            } else {
                // file:// and content:// (downloads) are opened directly.
                dataSpec
            }
        }

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    CacheDataSource.Factory()
                        .setCache(container.playbackCache.cache)
                        .setUpstreamDataSourceFactory(resolvingFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .setMediaButtonPreferences(mediaButtons(isFavorite = false))
            .build()

        restoreQueue(container)
        scope.launch {
            container.libraryRepository.observeFavoriteIds().collect { ids ->
                favoriteIds = ids
                refreshFavoriteUI()
                player?.let(::updateWidget)
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                rememberTracks(player)
                maybeRecordPlayback(player)
                updateWidget(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                rememberTrack(mediaItem)
                maybeRecordPlayback(exoPlayer)
                refreshFavoriteUI()
                persistQueue(container)
                updateWidgetWithRetry(exoPlayer)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                rememberTracks(exoPlayer)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                maybeRecordPlayback(exoPlayer)
                persistQueue(container)
                updateWidgetWithRetry(exoPlayer)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                persistQueue(container)
                updateWidgetWithRetry(exoPlayer)
            }

            override fun onPlayerError(error: PlaybackException) {
                updateWidget(exoPlayer)
            }
        })

        widgetTicker = scope.launch {
            var tick = 0
            while (isActive) {
                delay(500L)
                tick++
                val current = player ?: continue
                // Update twice per second while playing so the widget progress
                // bar advances smoothly. While paused, a slow heartbeat repairs
                // updates dropped by the launcher without a 2 Hz loop.
                if (current.playWhenReady || current.isPlaying) {
                    updateWidget(current)
                } else if (tick % 6 == 0) {
                    updateWidget(current)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        (application as? MusicApplication)?.let { restoreQueue(it.container) }
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> {
                val current = player ?: return super.onStartCommand(intent, flags, startId)
                when {
                    current.playbackState == Player.STATE_IDLE && current.mediaItemCount > 0 -> {
                        current.prepare()
                        current.play()
                    }
                    current.isPlaying -> current.pause()
                    else -> current.play()
                }
            }

            ACTION_NEXT -> {
                player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
            }

            ACTION_PREVIOUS -> {
                player?.let {
                    if (it.currentPosition > 3_000L) {
                        it.seekTo(0L)
                    } else if (it.hasPreviousMediaItem()) {
                        it.seekToPreviousMediaItem()
                    }
                }
            }

            ACTION_TOGGLE_FAVORITE -> toggleFavorite()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing when the task is swiped away, but don't leave an idle service.
        if (player?.playWhenReady != true) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        widgetTicker?.cancel()
        (application as? MusicApplication)?.let { persistQueue(it.container) }
        player?.let { current ->
            val item = current.currentMediaItem
            val track = TrackMediaItems.trackOf(item, (application as MusicApplication).container.json)
            val title = item?.mediaMetadata?.title?.toString() ?: "BanLiMusic"
            val artist = item?.mediaMetadata?.artist?.toString() ?: "还没有播放歌曲"
            val hasMedia = item != null
            val position = current.currentPosition.coerceAtLeast(0L)
            val duration = current.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            val artwork = item?.mediaMetadata?.artworkUri?.toString() ?: track?.artworkUrl
            val favorite = currentFavorite
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    PlayerWidgetUpdater.update(
                        context = this@MusicPlaybackService,
                        title = title,
                        artist = artist,
                        isPlaying = false,
                        hasMedia = hasMedia,
                        isFavorite = favorite,
                        artworkUrl = artwork,
                        positionMs = position,
                        durationMs = duration,
                    )
                }
            }
        }
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun restoreQueue(container: com.banlimusic.app.AppContainer) {
        val current = player ?: return
        if (current.mediaItemCount > 0) return
        val restored = PlaybackStateStore.load(this, container.json) ?: return
        val items = restored.tracks.map { TrackMediaItems.create(it, container.json) }
        if (items.isEmpty()) return
        current.setMediaItems(items, restored.index, restored.positionMs)
        updateWidget(current)
    }

    private fun persistQueue(container: com.banlimusic.app.AppContainer) {
        val current = player ?: return
        if (current.mediaItemCount == 0) return
        val tracks = (0 until current.mediaItemCount).mapNotNull { index ->
            TrackMediaItems.trackOf(current.getMediaItemAt(index), container.json)
        }
        if (tracks.size != current.mediaItemCount) return
        PlaybackStateStore.save(
            context = this,
            json = container.json,
            tracks = tracks,
            index = current.currentMediaItemIndex,
            positionMs = current.currentPosition,
        )
    }

    private fun rememberTracks(player: Player) {
        for (index in 0 until player.mediaItemCount) {
            rememberTrack(player.getMediaItemAt(index))
        }
    }

    private fun rememberTrack(item: MediaItem?) {
        val track = TrackMediaItems.trackOf(item, (application as MusicApplication).container.json)
            ?: return
        trackById[track.id] = track
    }

    private fun maybeRecordPlayback(player: Player) {
        if (!player.isPlaying) return
        val item = player.currentMediaItem ?: return
        if (item.mediaId == lastRecordedMediaId) return
        val track = TrackMediaItems.trackOf(item, (application as MusicApplication).container.json) ?: return
        lastRecordedMediaId = item.mediaId
        scope.launch(Dispatchers.IO) {
            (application as MusicApplication).container.libraryRepository.recordPlay(track)
        }
    }

    private fun updateWidgetWithRetry(player: Player) {
        updateWidget(player)
        scope.launch {
            delay(400L)
            player.let(::updateWidget)
            delay(1_200L)
            player.let(::updateWidget)
        }
    }

    private fun updateWidget(player: Player) {
        val container = (application as MusicApplication).container
        val item = player.currentMediaItem
        val track = TrackMediaItems.trackOf(item, container.json)
        val title = item?.mediaMetadata?.title?.toString() ?: "BanLiMusic"
        val artist = item?.mediaMetadata?.artist?.toString() ?: "还没有播放歌曲"
        // Use the intended state so the icon flips immediately while buffering.
        val isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        val hasMedia = item != null
        val isFavorite = item?.mediaId?.let { it in favoriteIds } ?: false
        val artworkUrl = item?.mediaMetadata?.artworkUri?.toString() ?: track?.artworkUrl
        val trackDuration = track?.durationMs ?: 0L
        val duration = when {
            player.duration > 0L && player.duration != C.TIME_UNSET -> player.duration
            trackDuration > 0L -> trackDuration
            else -> 0L
        }
        val position = player.currentPosition.coerceAtLeast(0L)

        scope.launch {
            runCatching {
                PlayerWidgetUpdater.update(
                    context = this@MusicPlaybackService,
                    title = title,
                    artist = artist,
                    isPlaying = isPlaying,
                    hasMedia = hasMedia,
                    isFavorite = isFavorite,
                    artworkUrl = artworkUrl,
                    positionMs = position,
                    durationMs = duration,
                )
            }
        }
    }

    private fun toggleFavorite() {
        val container = (application as MusicApplication).container
        val item = player?.currentMediaItem ?: return
        val track = TrackMediaItems.trackOf(item, container.json) ?: return
        scope.launch(Dispatchers.IO) {
            val category = container.settingsRepository.current().defaultFavoriteCategory
            runCatching { container.libraryRepository.toggleFavorite(track, category) }
            withContext(Dispatchers.Main.immediate) {
                refreshFavoriteUI()
                player?.let(::updateWidget)
            }
        }
    }

    private fun refreshFavoriteUI() {
        val favorite = player?.currentMediaItem?.mediaId?.let { it in favoriteIds } ?: false
        if (favorite == currentFavorite) return
        currentFavorite = favorite
        mediaSession?.setMediaButtonPreferences(mediaButtons(favorite))
    }

    private fun mediaButtons(isFavorite: Boolean): List<CommandButton> {
        val favorite = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setSessionCommand(FavoriteCommand.sessionCommand)
            .setDisplayName(if (isFavorite) "取消收藏" else "收藏")
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        val previous = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName("上一首")
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val next = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName("下一首")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
        return listOf(previous, favorite, next)
    }

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.banlimusic.app.action.TOGGLE_PLAY"
        const val ACTION_NEXT = "com.banlimusic.app.action.NEXT"
        const val ACTION_PREVIOUS = "com.banlimusic.app.action.PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "com.banlimusic.app.action.TOGGLE_FAVORITE"
    }
}
