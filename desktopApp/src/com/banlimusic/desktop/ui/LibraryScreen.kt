package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.sort.TrackSort
import com.banlimusic.app.domain.sort.TrackSorter
import com.banlimusic.desktop.DesktopPaths
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopSettingsRepository
import com.banlimusic.shared.ui.components.CompactTextButton
import com.banlimusic.shared.ui.components.ListTabLabel
import com.banlimusic.shared.ui.components.TRACK_LIST_HEADER_ITEM_COUNT
import com.banlimusic.shared.ui.components.TrackListHeaderSpec
import com.banlimusic.shared.ui.components.TrackListPanel
import com.banlimusic.shared.ui.components.TrackScreenHeader
import com.banlimusic.shared.ui.components.NewCategoryDialog
import com.banlimusic.shared.ui.components.TrackSelectionActionBar
import com.banlimusic.shared.ui.components.rememberSelectionActionsState
import com.banlimusic.shared.ui.components.rememberTrackSelectionState
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

private val LIBRARY_SORT_OPTIONS = listOf(
    TrackSort.TITLE,
    TrackSort.ARTIST,
    TrackSort.CREATED,
)

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
    var sort by remember { mutableStateOf(TrackSort.TITLE) }
    var ascending by remember { mutableStateOf(true) }
    var showClearLocal by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    val selection = rememberTrackSelectionState()
    val listState = rememberLazyListState()

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
    val sorted = remember(visible, sort, ascending) {
        TrackSorter.sort(visible, sort, ascending)
    }
    val sections = remember(sorted, sort) {
        TrackSorter.sections(sorted, sort).map {
            it.copy(itemIndex = it.itemIndex + TRACK_LIST_HEADER_ITEM_COUNT)
        }
    }
    val selectedTracks = sorted.filter { it.id in selection.selectedIds }
    val actionsState = rememberSelectionActionsState(
        selectionMode = selection.mode,
        selectedIds = selection.selectedIds,
        tracks = sorted,
        defaultCategory = settings.defaultFavoriteCategory,
        downloadQualities = { DesktopSettingsRepository.DESKTOP_QUALITIES },
    )
    val localDirectory = settings.localMusicDirectory.ifBlank {
        DesktopPaths.localMusicDir.toString()
    }

    LaunchedEffect(filter, query, folderFilter) {
        selection.exit()
    }
    LaunchedEffect(sorted) {
        selection.retain(sorted.mapTo(mutableSetOf()) { it.id })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TrackScreenHeader(title = "音乐库") {
            Text(
                text = "收藏 ${favorites.size} · 最近 ${history.size} · 本地 ${localTracks.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryTabRow(selectedTabIndex = filter) {
            Tab(
                selected = filter == 0,
                onClick = {
                    filter = 0
                    folderFilter = null
                },
                text = { ListTabLabel("收藏") },
            )
            Tab(
                selected = filter == 1,
                onClick = { filter = 1 },
                text = { ListTabLabel("最近播放") },
            )
            Tab(
                selected = filter == 2,
                onClick = { filter = 2 },
                text = { ListTabLabel("本地") },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                text = if (query.isBlank()) "共 ${sorted.size} 首" else "匹配 ${sorted.size} 首",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (filter == 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在扫描本地音乐…", style = MaterialTheme.typography.labelSmall)
                }
            }
            state.localScanError?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        TrackListPanel(
            items = sorted,
            itemKey = { it.id },
            validKey = { it.id },
            spec = TrackListHeaderSpec(
                countText = when (filter) {
                    0 -> "收藏 ${sorted.size} 首"
                    1 -> "最近播放 ${sorted.size} 首"
                    else -> "本地 ${sorted.size} 首"
                },
                selectionMode = selection.mode,
                selectedCount = selection.selectedIds.size,
                sort = sort,
                sortOptions = LIBRARY_SORT_OPTIONS,
                ascending = ascending,
                categories = if (filter == 0) categories else null,
                selectedCategory = folderFilter,
                allCategoryLabel = "全部收藏夹",
            ),
            listState = listState,
            sections = sections,
            ascending = ascending,
            selectionMode = selection.mode,
            onStartSelection = selection::enter,
            onSelect = selection::toggle,
            onSelectAll = { selection.selectAll(sorted.map { it.id }) },
            onCancelSelection = selection::exit,
            onSort = { sort = it },
            onToggleOrder = { ascending = !ascending },
            onCategory = { folderFilter = it },
            modifier = Modifier.weight(1f),
            countActions = {
                if (sorted.isNotEmpty()) {
                    CompactTextButton(
                        text = "选择",
                        onClick = {
                            selection.enter()
                            selection.selectAll(sorted.map { it.id })
                        },
                    )
                }
                CompactTextButton(
                    text = "新建收藏夹",
                    onClick = { showNewCategory = true },
                )
            },
            emptyContent = {
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
                )
            },
            selectionBar = {
                TrackSelectionActionBar(
                    categories = categories,
                    state = actionsState,
                    onFavorite = {
                        val categoriesToAdd = actionsState.chosenCategories.ifEmpty {
                            setOf(settings.defaultFavoriteCategory)
                        }
                        categoriesToAdd.forEach { category ->
                            state.addTracksToCategory(selectedTracks, category)
                        }
                        selection.exit()
                    },
                    onDownload = {
                        state.downloadTracks(
                            selectedTracks,
                            actionsState.chosenQuality ?: settings.downloadQualityKbps,
                        )
                        selection.exit()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    favoriteEnabled = selectedTracks.isNotEmpty() &&
                        actionsState.chosenCategories.isNotEmpty(),
                    downloadEnabled = filter != 2 &&
                        selectedTracks.isNotEmpty() &&
                        actionsState.qualities.isNotEmpty(),
                )
            },
        ) { _, track ->
            DesktopTrackRow(
                track = track,
                loader = state.container.artworkLoader,
                allowRemoteArtwork = settings.requestListArtwork,
                favorite = track.id in favoriteIds,
                current = playback.track?.id == track.id,
                onPlay = {
                    if (selection.mode) selection.toggle(track.id)
                    else state.play(track, sorted)
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

    if (showNewCategory) {
        NewCategoryDialog(
            onDismiss = { showNewCategory = false },
            onCreate = { name ->
                showNewCategory = false
                state.createFavoriteCategory(name) { category ->
                    if (selectedTracks.isNotEmpty()) {
                        state.addTracksToCategory(selectedTracks, category)
                        selection.exit()
                    }
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
                    Text(
                        "URI：${file?.toURI() ?: "未知"}",
                        style = MaterialTheme.typography.labelSmall,
                    )
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
