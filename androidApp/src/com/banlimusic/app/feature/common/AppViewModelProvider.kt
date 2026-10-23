package com.banlimusic.app.feature.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.feature.downloads.DownloadsViewModel
import com.banlimusic.app.feature.home.HomeViewModel
import com.banlimusic.app.feature.library.LibraryViewModel
import com.banlimusic.app.feature.player.PlayerViewModel
import com.banlimusic.app.feature.search.SearchViewModel
import com.banlimusic.app.feature.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                library = app().container.libraryRepository,
                settings = app().container.settingsRepository,
            )
        }
        initializer {
            SearchViewModel(
                music = app().container.musicRepository,
                library = app().container.libraryRepository,
                settings = app().container.settingsRepository,
            )
        }
        initializer {
            LibraryViewModel(library = app().container.libraryRepository)
        }
        initializer {
            DownloadsViewModel(
                downloads = app().container.downloadRepository,
                playback = app().container.playbackController,
            )
        }
        initializer {
            SettingsViewModel(
                settings = app().container.settingsRepository,
                music = app().container.musicRepository,
                library = app().container.libraryRepository,
            )
        }
        initializer {
            PlayerViewModel(
                playback = app().container.playbackController,
                library = app().container.libraryRepository,
                music = app().container.musicRepository,
                downloads = app().container.downloadRepository,
            )
        }
    }

    private fun CreationExtras.app(): MusicApplication =
        (this[APPLICATION_KEY] as MusicApplication)
}
