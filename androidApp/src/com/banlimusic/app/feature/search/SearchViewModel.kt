package com.banlimusic.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
    val favoriteIds: Set<String> = emptySet(),
)

class SearchViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<String>> = library.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enabledSources: StateFlow<Set<String>> = settings.settings
        .map { it.enabledSources }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf("netease"))

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            library.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, error = null) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    page = 0,
                    hasMore = false,
                    isSearching = false,
                    isLoadingMore = false,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300L)
            performSearch(reset = true)
        }
    }

    fun submit() {
        if (_uiState.value.query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(reset = true) }
    }

    fun useHistory(query: String) {
        onQueryChange(query)
        submit()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isSearching || state.isLoadingMore || !state.hasMore) return
        searchJob = viewModelScope.launch { performSearch(reset = false) }
    }

    fun retry() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(reset = true) }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { library.toggleFavorite(track) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }

    private suspend fun performSearch(reset: Boolean) {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        val page = if (reset) 1 else _uiState.value.page + 1
        _uiState.update {
            it.copy(
                isSearching = reset,
                isLoadingMore = !reset,
                error = null,
            )
        }
        try {
            val result = music.search(query, page)
            if (query != _uiState.value.query.trim()) return
            library.addSearchQuery(query)
            _uiState.update { previous ->
                val combined = if (reset) {
                    result.tracks
                } else {
                    (previous.results + result.tracks).distinctBy { it.id }
                }
                previous.copy(
                    results = combined,
                    page = result.page,
                    hasMore = result.hasMore,
                    isSearching = false,
                    isLoadingMore = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _uiState.update {
                it.copy(
                    results = if (reset) emptyList() else it.results,
                    isSearching = false,
                    isLoadingMore = false,
                    error = error.userMessage(),
                )
            }
        }
    }
}
