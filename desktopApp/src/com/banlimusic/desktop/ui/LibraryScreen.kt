package com.banlimusic.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track
import com.banlimusic.desktop.DesktopPaths
import com.banlimusic.desktop.data.DesktopDownloadStatus
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

@Composable
fun DesktopLibraryScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val playback by state.playback.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()
    val localTracks by state.localTracks.collectAsState()
    val folders by state.favoriteFolders.collectAsState()
    val categories by state.favoriteCategories.collectAsState()
    val downloads by state.downloads.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var showNewCategory by remember { mutableStateOf(false) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var showClearLocal by remember { mutableStateOf(false) }
    val selection = rememberTrackSelectionState()

    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    val downloadedIds = downloads
        .filter { it.status == DesktopDownloadStatus.COMPLETED }
        .mapTo(mutableSetOf()) { it.track.id }
    val source = when (filter) {
        0 -> if (folderFilter == null) {
            favorites
        } else {
            favorites.filter { folders[it.id] == folderFilter }
        }

        1 -> history
        else -> localTracks
    }
    val visible = remember(source, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            source
        } else {
            source.filter { track ->
                track.title.contains(keyword, ignoreCase = true) ||
                    track.artistLine.contains(keyword, ignoreCase = true) ||
                    track.album?.title?.contains(keyword, ignoreCase = true) == true
            }
        }
    }
    val selectedTracks = visible.filter { it.id in selection.selectedIds }
    val localDirectory = settings.localMusicDirectory.ifBlank {
        DesktopPaths.localMusicDir.toString()
    }

    LaunchedEffect(filter, query, folderFilter) {
        selection.exit()
    }
    LaunchedEffect(visible) {
        selection.retain(visible.mapTo(mutableSetOf()) { it.id })
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp)) {
        Text(
            text = "音乐库",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "收藏 ${favorites.size} 首，最近播放 ${history.size} 首，本地 ${localTracks.size} 首",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == 0,
                onClick = {
                    filter = 0
                    folderFilter = null
                },
                label = { Text("我的收藏") },
            )
            FilterChip(
                selected = filter == 1,
                onClick = { filter = 1 },
                label = { Text("最近播放") },
            )
            FilterChip(
                selected = filter == 2,
                onClick = { filter = 2 },
                label = { Text("本地") },
            )
        }

        if (filter == 0 && categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = folderFilter == null,
                    onClick = { folderFilter = null },
                    label = { Text("全部收藏夹") },
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = folderFilter == category,
                        onClick = { folderFilter = category },
                        label = { Text(category) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(0.62f),
                singleLine = true,
                placeholder = {
                    Text(
                        when (filter) {
                            0 -> "在收藏中筛选"
                            1 -> "在播放记录中筛选"
                            else -> "在本地音乐中筛选"
                        },
                    )
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (query.isBlank()) "共 ${visible.size} 首" else "匹配 ${visible.size} 首",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (visible.isNotEmpty()) {
                TextButton(
                    onClick = {
                        if (selection.mode) selection.exit() else selection.enter()
                    },
                ) {
                    Text(if (selection.mode) "取消选择" else "选择")
                }
            }
        }

        if (filter == 2) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "目录：$localDirectory",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                TextButton(
                    onClick = {
                        chooseLocalDirectory(localDirectory)?.let { directory ->
                            state.container.settingsRepository.setLocalMusicDirectory(directory)
                            state.scanLocalMusic(directory)
                        }
                    },
                ) { Text("选择目录并扫描") }
                TextButton(
                    onClick = { state.scanLocalMusic(localDirectory) },
                    enabled = !state.localScanning,
                ) { Text("扫描") }
                TextButton(
                    onClick = { showClearLocal = true },
                    enabled = localTracks.isNotEmpty(),
                ) { Text("清空") }
            }
            if (state.localScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在扫描本地音乐…", style = MaterialTheme.typography.labelSmall)
                }
            }
            state.localScanError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (selection.mode) {
            Spacer(Modifier.height(8.dp))
            TrackSelectionBar(
                selection = selection,
                selectableIds = visible.map { it.id },
                categories = categories,
                onFavorite = { category ->
                    state.addTracksToCategory(selectedTracks, category)
                    selection.exit()
                },
                onCreateCategory = { showNewCategory = true },
                onDownload = { quality ->
                    state.downloadTracks(selectedTracks, quality)
                    selection.exit()
                },
                initialQuality = settings.downloadQualityKbps,
                allowDownload = filter != 2,
            )
        }

        Spacer(Modifier.height(10.dp))
        if (visible.isEmpty()) {
            DesktopEmptyState(
                title = when {
                    filter == 0 && favorites.isEmpty() -> "还没有收藏歌曲"
                    filter == 0 -> "该收藏夹没有歌曲"
                    filter == 1 && history.isEmpty() -> "还没有播放记录"
                    filter == 2 && localTracks.isEmpty() -> "还没有扫描本地音乐"
                    else -> "没有匹配的歌曲"
                },
                description = when {
                    filter == 2 && localTracks.isEmpty() ->
                        "选择音乐目录并扫描后，本地歌曲会显示在这里。"
                    filter == 0 && favorites.isEmpty() ->
                        "在搜索结果中点击心形图标即可加入桌面音乐库。"
                    filter == 1 && history.isEmpty() -> "播放歌曲后会自动记录在这里。"
                    else -> "试试更短的关键词或切换栏目。"
                },
                glyph = "♫",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(visible, key = { it.id }) { track ->
                    DesktopTrackRow(
                        track = track,
                        loader = state.container.artworkLoader,
                        allowRemoteArtwork = settings.requestListArtwork,
                        favorite = track.id in favoriteIds,
                        current = playback.track?.id == track.id,
                        onPlay = {
                            if (selection.mode) selection.toggle(track.id)
                            else state.play(track, visible)
                        },
                        onToggleFavorite = { state.toggleFavorite(track) },
                        onDownload = if (filter == 2) null else {
                            { state.download(track) }
                        },
                        showFavorite = filter == 1,
                        downloaded = track.id in downloadedIds,
                        categoryLabel = folders[track.id],
                        selected = if (selection.mode) track.id in selection.selectedIds else null,
                        onInfo = if (filter == 2) {
                            { infoTrack = track }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (showNewCategory) {
        NewCategoryDialog(
            onDismiss = { showNewCategory = false },
            onCreate = { name ->
                showNewCategory = false
                state.createFavoriteCategory(name) { category ->
                    state.addTracksToCategory(selectedTracks, category)
                    selection.exit()
                }
            },
        )
    }

    infoTrack?.let { track ->
        val file = localFile(track)
        AlertDialog(
            onDismissRequest = { infoTrack = null },
            title = { Text(track.title) },
            text = {
                Column {
                    Text("歌手：${track.artistLine}")
                    track.album?.title?.let { Text("专辑：$it") }
                    Text("文件：${file?.absolutePath ?: "未知"}")
                    Text("URI：${file?.toURI() ?: "未知"}", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        file?.parentFile?.let(::openDirectory)
                        infoTrack = null
                    },
                ) { Text("打开目录") }
            },
            dismissButton = {
                TextButton(onClick = { infoTrack = null }) { Text("关闭") }
            },
        )
    }

    if (showClearLocal) {
        AlertDialog(
            onDismissRequest = { showClearLocal = false },
            title = { Text("清空本地音乐索引") },
            text = { Text("只移除列表记录，不会删除磁盘上的音频文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLocal = false
                        state.clearLocalMusic()
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLocal = false }) { Text("取消") }
            },
        )
    }
}

private fun localFile(track: Track): File? = track.sources
    .firstNotNullOfOrNull { it.localUri }
    ?.let { uri -> runCatching { File(URI(uri)) }.getOrNull() }

private fun chooseLocalDirectory(initial: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "选择本地音乐目录"
        currentDirectory = File(initial).takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun openDirectory(directory: File) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(directory)
    }
}
