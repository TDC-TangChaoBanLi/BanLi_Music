/**
 * 文件作用：音乐库的 Android 适配层，向共享音乐库界面提供数据、权限与平台选择器。
 */
package com.banlimusic.app.feature.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.feature.common.SourcePickerDialog
import com.banlimusic.app.ui.components.TrackArtwork
import com.banlimusic.shared.ui.screens.BanLiLibraryScreen
import com.banlimusic.shared.ui.screens.LibraryActions
import com.banlimusic.shared.ui.screens.LibraryScreenController
import com.banlimusic.shared.ui.screens.LibraryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LibraryScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val favorites by viewModel.allFavorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val importedTracks by viewModel.importedTracks.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val favoriteFolders by viewModel.favoriteFolders.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val preferredSources by viewModel.preferredSources.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val controller = remember { LibraryScreenController() }
    var sourcePickerTrack by remember { mutableStateOf<Track?>(null) }

    BackHandler { if (!controller.handleBack()) onExitApp() }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.importLocalMusic()
        } else {
            Toast.makeText(context, "需要媒体读取权限才能扫描本地音乐", Toast.LENGTH_SHORT).show()
        }
    }
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.importLocalFiles(uris)
    }
    val importFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importLocalFolder(uri)
        }
    }

    fun requestQuickImport() {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.importLocalMusic() else permissionLauncher.launch(permission)
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val actions = remember(viewModel, onPlayQueue, context) {
        object : LibraryActions {
            override fun playQueue(tracks: List<Track>, index: Int) {
                onPlayQueue(tracks, index)
            }

            override fun toggleFavorite(track: Track) = viewModel.toggleFavorite(track)

            override fun addFavorites(tracks: List<Track>, folders: Set<String>) =
                viewModel.addFavorites(tracks, folders)

            override fun removeFavorites(tracks: List<Track>, folder: String?) =
                viewModel.removeFavorites(tracks, folder)

            override fun download(tracks: List<Track>, qualityKbps: Int?) =
                viewModel.downloadSelected(tracks, qualityKbps)

            override suspend fun downloadQualities(tracks: List<Track>): List<Int> =
                viewModel.downloadQualities(tracks)

            override fun createCategory(name: String) = viewModel.createCategory(name)

            override fun reorderCategories(categories: List<String>) =
                viewModel.saveCategoryOrder(categories)

            override fun deleteCategory(name: String) = viewModel.deleteCategory(name)

            override fun clearHistory() = viewModel.clearHistory()

            override fun deleteHistory(tracks: List<Track>) =
                viewModel.deleteHistorySelection(tracks)

            override fun deleteLocal(tracks: List<Track>) =
                viewModel.deleteLocalSelection(tracks)

            override fun deleteDownloaded(tracks: List<Track>) =
                viewModel.removeDownloadedSelection(tracks)

            override fun importScan() = requestQuickImport()

            override fun importFiles() =
                importFilesLauncher.launch(arrayOf("audio/*"))

            override fun importFolder() = importFolderLauncher.launch(null)

            override suspend fun localLocation(track: Track): String =
                withContext(Dispatchers.IO) {
                    resolveLocalLocation(context, track)
                }

            override fun openLocation(track: Track) {
                val location = track.sources.firstNotNullOfOrNull { it.localUri }
                Toast.makeText(
                    context,
                    location?.let { Uri.parse(it).path } ?: "位置未知",
                    Toast.LENGTH_LONG,
                ).show()
            }

            override fun onSourceClick(track: Track) {
                sourcePickerTrack = track
            }
        }
    }

    BanLiLibraryScreen(
        state = LibraryUiState(
            favorites = favorites,
            history = history,
            importedTracks = importedTracks,
            downloadedTracks = downloadedTracks,
            downloadedBytes = downloadedBytes,
            categories = categories,
            defaultCategory = defaultCategory,
            favoriteFolders = favoriteFolders,
            favoriteIds = favoriteIds,
            downloadedIds = downloadedIds,
            preferredSources = preferredSources,
        ),
        actions = actions,
        artwork = { track, artworkModifier ->
            TrackArtwork(track = track, modifier = artworkModifier)
        },
        modifier = Modifier.fillMaxSize(),
        controller = controller,
    )

    sourcePickerTrack?.let { track ->
        SourcePickerDialog(
            track = track,
            preferredProviderId = preferredSources[track.id],
            onSelect = { providerId ->
                viewModel.selectSourcePreference(track.id, providerId)
                sourcePickerTrack = null
            },
            onDismiss = { sourcePickerTrack = null },
        )
    }
}

private fun resolveLocalLocation(context: Context, track: Track): String {
    val uriString = track.sources.firstNotNullOfOrNull { it.localUri } ?: return "位置未知"
    val uri = Uri.parse(uriString)
    if (uri.scheme == "file") {
        return File(uri.path.orEmpty()).parent ?: uriString
    }
    return runCatching {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME,
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
            )
        }
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use uriString
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val relative = if (relativeIndex >= 0) cursor.getString(relativeIndex) else null
            val data = if (dataIndex >= 0) cursor.getString(dataIndex) else null
            when {
                !relative.isNullOrBlank() -> {
                    val directory = relative.trimEnd('/')
                    if (name.isNullOrBlank()) directory else "$directory/$name"
                }

                !data.isNullOrBlank() -> File(data).parent ?: data
                else -> uriString
            }
        } ?: uriString
    }.getOrDefault(uriString)
}
