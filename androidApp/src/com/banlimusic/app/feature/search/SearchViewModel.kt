/**
 * 文件作用：处理搜索请求、分页、选择状态与批量收藏下载。
 */
package com.banlimusic.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.common.userMessage
import com.banlimusic.app.core.datastore.SettingsRepository
import com.banlimusic.app.core.datastore.SourcePreferenceStore
import com.banlimusic.app.core.datastore.orderFavoriteCategories
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.DownloadRepository
import com.banlimusic.app.data.repository.LibraryRepository
import com.banlimusic.app.data.repository.MusicRepository
import com.banlimusic.app.domain.download.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
    val favoriteIds: Set<String> = emptySet(),
    val favoriteFolders: Map<String, Set<String>> = emptyMap(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

class SearchViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val sourcePreferences: SourcePreferenceStore,
) : ViewModel() {

    /** trackId → preferred provider picked from the (+n) badge. */
    val preferredSources: StateFlow<Map<String, String>> = sourcePreferences.preferredSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val history: StateFlow<List<String>> = library.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enabledSources: StateFlow<Set<String>> = settings.settings
        .map { it.enabledSources }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf("netease"))

    val defaultFavoriteCategory: StateFlow<String> = settings.settings
        .map { it.defaultFavoriteCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "默认收藏")

    val favoriteCategories: StateFlow<List<String>> = combine(
        library.observeFavoriteCategories(),
        settings.settings,
    ) { folders, appSettings ->
        orderFavoriteCategories(folders, appSettings.favoriteCategoryOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Application track ids whose audio is already downloaded. */
    val downloadedIds: StateFlow<Set<String>> = downloads.observeTasks()
        .map { tasks ->
            tasks
                .filter { it.status == DownloadStatus.COMPLETED && !it.filePath.isNullOrBlank() }
                .map { it.track.id }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            library.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
        viewModelScope.launch {
            library.observeFavoriteFolders().collect { folders ->
                _uiState.update { it.copy(favoriteFolders = folders) }
            }
        }
    }

    /**
     * Typing only updates the query. A request is sent when the user explicitly
     * presses the search button or the IME action, which keeps API usage low.
     */
    fun onQueryChange(value: String) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = value,
                results = emptyList(),
                page = 0,
                hasMore = false,
                hasSearched = false,
                isSearching = false,
                isLoadingMore = false,
                error = null,
                selectionMode = false,
                selectedIds = emptySet(),
            )
        }
    }

    fun submit() {
        if (_uiState.value.query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(reset = true) }
    }

    fun useHistory(query: String) {
        _uiState.update { it.copy(query = query) }
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
        viewModelScope.launch {
            library.toggleFavorite(track, defaultFavoriteCategory.value)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                selectionMode = enabled,
                selectedIds = if (enabled) it.selectedIds else emptySet(),
            )
        }
    }

    fun toggleSelection(trackId: String) {
        _uiState.update { state ->
            val selected = if (trackId in state.selectedIds) {
                state.selectedIds - trackId
            } else {
                state.selectedIds + trackId
            }
            state.copy(selectedIds = selected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.results.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    /** Quality tiers for [tracks], defaulting to the current selection. */
    suspend fun downloadQualities(
        tracks: Collection<Track> = _uiState.value.results.filter {
            it.id in _uiState.value.selectedIds
        },
    ): List<Int> {
        if (tracks.isEmpty()) return emptyList()
        return runCatching { music.downloadQualities(tracks) }.getOrDefault(emptyList())
    }

    fun batchFavorite(categories: Set<String> = setOf(defaultFavoriteCategory.value)) {
        val state = _uiState.value
        val tracks = state.results.filter { it.id in state.selectedIds }
        if (tracks.isEmpty() || categories.isEmpty()) return
        viewModelScope.launch {
            library.addFavorites(tracks, categories)
            _message.value = "已收藏 ${tracks.size} 首到“${categories.joinToString("、")}”"
            clearSelection()
        }
    }

    fun batchDownload(qualityKbps: Int? = null) {
        val state = _uiState.value
        val tracks = state.results.filter { it.id in state.selectedIds }
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val queued = downloads.enqueueAll(tracks, qualityKbps = qualityKbps)
            _message.value = "已加入下载队列：$queued 首"
            clearSelection()
        }
    }

    fun setPreferredSource(trackId: String, providerId: String?) {
        viewModelScope.launch { sourcePreferences.setPreferred(trackId, providerId) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun performSearch(reset: Boolean) {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        val page = if (reset) 1 else _uiState.value.page + 1
        val pageSize = settings.current().searchPageSize
        _uiState.update {
            it.copy(
                isSearching = reset,
                isLoadingMore = !reset,
                error = null,
            )
        }
        try {
            val result = music.search(query, page, pageSize)
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
                    hasSearched = true,
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
                    hasSearched = true,
                    isSearching = false,
                    isLoadingMore = false,
                    error = error.userMessage(),
                )
            }
        }
    }
}
