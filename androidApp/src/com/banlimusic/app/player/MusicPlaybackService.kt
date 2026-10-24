package com.banlimusic.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.banlimusic.app.MainActivity
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.network.JsonHttpClient
import com.banlimusic.app.domain.playback.StreamUriCodec
import com.banlimusic.app.widget.PlayerWidgetUpdater
import com.banlimusic.app.widget.WidgetArtworkStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastRecordedMediaId: String? = null
    private var lastWidgetUpdate = 0L
    private var widgetTicker: Job? = null
    private var widgetArtworkJob: Job? = null
    @Volatile
    private var widgetArtworkUrl: String? = null
    @Volatile
    private var widgetArtworkUri: String? = null

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
                val encoded = uri.lastPathSegment.orEmpty()
                val candidates = StreamUriCodec.decode(encoded)
                dataSpec.withUri(container.streamResolver.resolveBlocking(candidates))
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
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                maybeRecordPlayback(player)
                updateWidget(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                maybeRecordPlayback(exoPlayer)
                updateWidget(exoPlayer)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                maybeRecordPlayback(exoPlayer)
                updateWidget(exoPlayer)
            }

            override fun onPlayerError(error: PlaybackException) {
                updateWidget(exoPlayer)
            }
        })

        widgetTicker = scope.launch {
            while (isActive) {
                delay(1_000L)
                player?.let { updateWidget(it) }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        widgetArtworkJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        scope.cancel()
        super.onDestroy()
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

    private fun updateWidget(player: Player) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWidgetUpdate < 400L) return
        lastWidgetUpdate = now
        val container = (application as MusicApplication).container
        val item = player.currentMediaItem
        val track = TrackMediaItems.trackOf(item, container.json)
        val title = item?.mediaMetadata?.title?.toString() ?: "BanLiMusic"
        val artist = item?.mediaMetadata?.artist?.toString() ?: "还没有播放歌曲"
        val isPlaying = player.isPlaying
        val hasMedia = item != null
        val artworkUrl = item?.mediaMetadata?.artworkUri?.toString()
            ?: track?.artworkUrl
        val trackDuration = track?.durationMs ?: 0L
        val duration = when {
            player.duration > 0L && player.duration != C.TIME_UNSET -> player.duration
            trackDuration > 0L -> trackDuration
            else -> 0L
        }
        val position = player.currentPosition.coerceAtLeast(0L)

        if (artworkUrl.isNullOrBlank()) {
            widgetArtworkUrl = null
            widgetArtworkUri = null
        } else if (artworkUrl != widgetArtworkUrl) {
            widgetArtworkUrl = artworkUrl
            widgetArtworkUri = null
            widgetArtworkJob?.cancel()
            widgetArtworkJob = scope.launch {
                widgetArtworkUri = withContext(Dispatchers.IO) {
                    WidgetArtworkStore.write(this@MusicPlaybackService, artworkUrl)
                }
                updateWidget(player)
            }
        }

        val artworkUri = widgetArtworkUri
        scope.launch(Dispatchers.Default) {
            PlayerWidgetUpdater.update(
                context = this@MusicPlaybackService,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                hasMedia = hasMedia,
                artworkUri = artworkUri,
                positionMs = position,
                durationMs = duration,
            )
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.banlimusic.app.action.TOGGLE_PLAY"
        const val ACTION_NEXT = "com.banlimusic.app.action.NEXT"
        const val ACTION_PREVIOUS = "com.banlimusic.app.action.PREVIOUS"
    }
}
