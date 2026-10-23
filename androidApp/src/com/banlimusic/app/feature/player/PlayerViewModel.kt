package com.banlimusic.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.player.PlaybackController
import com.banlimusic.app.player.PlaybackUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playback.state

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
                    _artworkUrl.value = track?.artworkUrl
                    _lyrics.value = LyricsUiState()
                    if (track != null) loadArtwork(track)
                }
        }
    }

    private fun loadArtwork(track: Track) {
        artworkJob = viewModelScope.launch {
            if (track.artworkUrl != null) return@launch
            try {
                val url = music.artwork(track) ?: return@launch
                _artworkUrl.value = url
                playback.updateArtwork(track.id, url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Artwork is optional; keep the placeholder.
            }
        }
    }

    fun ensureLyrics() {
        val track = playback.state.value.currentTrack ?: return
        if (_lyrics.value.requested && _lyrics.value.lyrics != null) return
        if (_lyrics.value.loading) return
        lyricsJob?.cancel()
        _lyrics.value = LyricsUiState(loading = true, requested = true)
        lyricsJob = viewModelScope.launch {
            try {
                val lyrics = music.lyrics(track)
                _lyrics.value = LyricsUiState(lyrics = lyrics, requested = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _lyrics.value = LyricsUiState(error = error.userMessage(), requested = true)
            }
        }
    }

    fun reloadLyrics() {
        _lyrics.value = LyricsUiState()
        ensureLyrics()
    }

    fun downloadCurrent(qualityKbps: Int? = null) {
        val track = playback.state.value.currentTrack ?: return
        viewModelScope.launch {
            _message.value = downloads.enqueue(track, qualityKbps = qualityKbps).fold(
                onSuccess = { "已加入下载队列" },
                onFailure = { it.userMessage() },
            )
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

    fun clearError() = playback.clearError()
}
