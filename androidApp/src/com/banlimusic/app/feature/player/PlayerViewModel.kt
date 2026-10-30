/**
 * 文件作用：管理播放页状态、歌词加载、收藏与下载音质选择。
 */
package com.banlimusic.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.domain.download.DownloadRequestManager
import com.banlimusic.app.player.PlaybackController
import com.banlimusic.app.player.PlaybackUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LyricsUiState(
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    val error: String? = null,
    val requested: Boolean = false,
)

class PlayerViewModel(
    private val playback: PlaybackController,
    private val library: LibraryRepository,
    private val music: MusicRepository,
    private val downloadRequests: DownloadRequestManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playback.state

    val preferredSources: StateFlow<Map<String, String>> = playback.preferredSources

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val favoriteIds: StateFlow<Set<String>> = library.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val favoriteCategories: StateFlow<List<String>> = library.observeFavoriteCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _artworkUrl = MutableStateFlow<String?>(null)
    val artworkUrl: StateFlow<String?> = _artworkUrl.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var artworkJob: Job? = null
    private var lyricsJob: Job? = null
    private var loadedTrackId: String? = null
    private var lyricsTrackId: String? = null

    /** True while the lyrics page is on screen (including after ON_RESUME). */
    private var lyricsVisible = false

    init {
        viewModelScope.launch {
            playback.state
                .map { it.currentTrack }
                .distinctUntilChangedBy { it?.id }
                .collect { track ->
                    if (track?.id == loadedTrackId) return@collect
                    loadedTrackId = track?.id
                    artworkJob?.cancel()
                    lyricsJob?.cancel()
                    lyricsTrackId = null
                    _artworkUrl.value = track?.artworkUrl
                    _lyrics.value = LyricsUiState()
                    if (track != null) {
                        loadArtwork(track)
                        // Auto-advance in the background must refresh lyrics when
                        // the user returns to the still-open lyrics page.
                        if (lyricsVisible) ensureLyrics()
                    }
                }
        }
    }

    /** Called by the player screen when the lyrics page becomes visible/resumed. */
    fun setLyricsVisible(visible: Boolean) {
        lyricsVisible = visible
        if (visible) ensureLyrics()
    }

    private fun loadArtwork(track: Track) {
        artworkJob = viewModelScope.launch {
            val attempts = appSettings.value.playbackRetryCount.coerceIn(0, 10) + 1
            repeat(attempts) { attempt ->
                try {
                    val url = music.artwork(track) ?: return@launch
                    _artworkUrl.value = url
                    playback.updateArtwork(track.id, url)
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    if (attempt < attempts - 1) delay(RETRY_BASE_DELAY_MS shl attempt)
                }
            }
        }
    }

    fun ensureLyrics() {
        val track = playback.state.value.currentTrack ?: return
        val current = _lyrics.value
        if (lyricsTrackId == track.id) {
            if (current.loading) return
            // A successful (or empty) response is final for this track; only a
            // previous error is retried when the page is shown again.
            if (current.requested && (current.lyrics != null || current.error == null)) return
        }
        lyricsJob?.cancel()
        lyricsTrackId = track.id
        _lyrics.value = LyricsUiState(loading = true, requested = true)
        lyricsJob = viewModelScope.launch {
            val attempts = appSettings.value.playbackRetryCount.coerceIn(0, 10) + 1
            var lastError: String? = null
            repeat(attempts) { attempt ->
                try {
                    val lyrics = music.lyrics(track)
                    _lyrics.value = LyricsUiState(lyrics = lyrics, requested = true)
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    lastError = error.userMessage()
                    if (attempt < attempts - 1) delay(RETRY_BASE_DELAY_MS shl attempt)
                }
            }
            _lyrics.value = LyricsUiState(error = lastError, requested = true)
        }
    }

    fun reloadLyrics() {
        lyricsTrackId = null
        _lyrics.value = LyricsUiState()
        ensureLyrics()
    }

    /** Chooses the preferred provider for the current track and restarts it in place. */
    fun selectSource(providerId: String?) {
        playback.switchSource(providerId)
    }

    fun downloadCurrent(qualityKbps: Int? = null) {
        val track = playback.state.value.currentTrack ?: return
        downloadRequests.download(listOf(track), qualityKbps) { outcome ->
            _message.value = outcome.summary()
        }
    }

    suspend fun downloadQualities(): List<Int> {
        val track = playback.state.value.currentTrack ?: return emptyList()
        return runCatching { music.downloadQualities(listOf(track)) }
            .getOrDefault(emptyList())
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun toggleFavorite() {
        val track = playback.state.value.currentTrack ?: return
        viewModelScope.launch {
            library.toggleFavorite(track, appSettings.value.defaultFavoriteCategory)
        }
    }

    fun addFavoriteToCategory(category: String) {
        val track = playback.state.value.currentTrack ?: return
        viewModelScope.launch {
            library.addFavorite(track, category)
            _message.value = "已收藏到“$category”"
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun next() = playback.next()

    fun previous() = playback.previous()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun toggleShuffle() = playback.toggleShuffle()

    fun cycleRepeatMode() = playback.cycleRepeatMode()

    fun cyclePlayMode() = playback.cyclePlayMode()

    fun clearError() = playback.clearError()

    private companion object {
        const val RETRY_BASE_DELAY_MS = 2_000L
    }
}
