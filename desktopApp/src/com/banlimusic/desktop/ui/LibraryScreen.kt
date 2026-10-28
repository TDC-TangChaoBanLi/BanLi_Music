package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.banlimusic.app.core.model.Track
import com.banlimusic.desktop.DesktopPaths
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.shared.ui.screens.BanLiLibraryScreen
import com.banlimusic.shared.ui.screens.LibraryActions
import com.banlimusic.shared.ui.screens.LibraryUiState
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

/**
 * Desktop adapter for the shared library screen. Only file pickers and
 * platform persistence are implemented here; all layout lives in `shared`.
 */
@Composable
fun DesktopLibraryScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()
    val localTracks by state.localTracks.collectAsState()
    val folders by state.favoriteFolders.collectAsState()
    val categories by state.favoriteCategories.collectAsState()
    val downloads by state.downloads.collectAsState()

    val completed = remember(downloads) {
        downloads.filter { it.status == DesktopDownloadStatus.COMPLETED }
    }
    val actions = remember(state) {
        object : LibraryActions {
            override fun playQueue(tracks: List<Track>, index: Int) {
                if (tracks.isEmpty()) return
                val safe = index.coerceIn(tracks.indices)
                state.play(tracks[safe], tracks)
            }

            override fun toggleFavorite(track: Track) = state.toggleFavorite(track)

            override fun addFavorites(tracks: List<Track>, folders: Set<String>) =
                state.addTracksToCategories(tracks, folders)

            override fun removeFavorites(tracks: List<Track>, folder: String?) =
                state.removeFavorites(tracks, folder)

            override fun download(tracks: List<Track>, qualityKbps: Int?) =
                state.downloadTracks(
                    tracks,
                    qualityKbps ?: state.settings.value.downloadQualityKbps,
                )

            override suspend fun downloadQualities(tracks: List<Track>): List<Int> =
                DesktopSettingsRepository.DESKTOP_QUALITIES

            override fun createCategory(name: String) =
                state.createFavoriteCategory(name) {}

            override fun reorderCategories(categories: List<String>) =
                state.saveFavoriteCategoryOrder(categories)

            override fun deleteCategory(name: String) =
                state.deleteFavoriteCategory(name)

            override fun clearHistory() = state.clearHistory()

            override fun deleteHistory(tracks: List<Track>) =
                state.removeHistory(tracks)

            override fun deleteLocal(tracks: List<Track>) =
                state.removeLocalTracks(tracks)

            override fun deleteDownloaded(tracks: List<Track>) =
                state.removeDownloaded(tracks)

            override fun importScan() {
                chooseDirectory(
                    state.settings.value.localMusicDirectory.ifBlank {
                        DesktopPaths.localMusicDir.toString()
                    },
                    "选择本地音乐目录",
                )?.let { directory ->
                    state.container.settingsRepository.setLocalMusicDirectory(directory)
                    state.scanLocalMusic(directory)
                }
            }

            override fun importFiles() {
                chooseAudioFiles(
                    state.settings.value.localMusicDirectory.ifBlank {
                        DesktopPaths.localMusicDir.toString()
                    },
                ).let(state::importLocalFiles)
            }

            override fun importFolder() = importScan()

            override suspend fun localLocation(track: Track): String {
                val uri = track.sources.firstNotNullOfOrNull { it.localUri }
                    ?: return "位置未知"
                return runCatching {
                    File(URI(uri)).parent ?: uri
                }.getOrDefault(uri)
            }

            override fun openLocation(track: Track) {
                val uri = track.sources.firstNotNullOfOrNull { it.localUri } ?: return
                runCatching {
                    val parent = File(URI(uri)).parentFile ?: return
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(parent)
                }
            }
        }
    }

    BanLiLibraryScreen(
        state = LibraryUiState(
            favorites = favorites,
            history = history,
            importedTracks = localTracks,
            downloadedTracks = completed.map { it.track },
            downloadedBytes = completed.sumOf { it.totalBytes },
            categories = categories,
            defaultCategory = settings.defaultFavoriteCategory,
            favoriteFolders = folders,
            favoriteIds = favorites.mapTo(mutableSetOf()) { it.id },
            downloadedIds = completed.mapTo(mutableSetOf()) { it.track.id },
            preferredSources = emptyMap(),
            supportsSourcePicker = false,
        ),
        actions = actions,
        artwork = { track, artworkModifier ->
            DesktopTrackArtwork(
                track = track,
                loader = state.container.artworkLoader,
                allowRemote = settings.requestListArtwork,
                modifier = artworkModifier,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun chooseDirectory(initial: String, title: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        currentDirectory = File(initial).takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun chooseAudioFiles(initial: String): List<File> {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        dialogTitle = "选择音频文件"
        currentDirectory = File(initial).takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "音频文件",
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma",
        )
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.orEmpty().toList()
    } else {
        emptyList()
    }
}
