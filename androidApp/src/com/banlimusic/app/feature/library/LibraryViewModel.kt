package com.banlimusic.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val library: LibraryRepository,
) : ViewModel() {
    val favorites: StateFlow<List<Track>> = library.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<Track>> = library.observeHistory(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeFavorite(track: Track) {
        viewModelScope.launch { library.toggleFavorite(track) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }
}
