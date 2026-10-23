package com.banlimusic.app.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.TrackRow

@Composable
fun LibraryScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.importLocalMusic()
        } else {
            Toast.makeText(context, "需要媒体读取权限才能导入本地音乐", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestLocalImport() {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.importLocalMusic()
        } else {
            importLauncher.launch(permission)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "音乐库",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("收藏") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("最近播放") },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("本地") },
            )
        }

        when (selectedTab) {
            0 -> FavoritesTab(
                favorites = favorites,
                categories = categories,
                selectedCategory = selectedCategory,
                defaultCategory = defaultCategory,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onSelectCategory = viewModel::setCategory,
                onPlayQueue = onPlayQueue,
                onRemoveFavorite = viewModel::removeFavorite,
                onSelectionModeChange = {
                    selectionMode = it
                    if (!it) viewModel.clearSelection()
                },
                onToggleSelection = viewModel::toggleSelection,
                onSelectAll = { viewModel.selectAll(favorites) },
                onDownloadSelected = { viewModel.downloadSelected(favorites) },
            )

            1 -> HistoryTab(
                history = history,
                onPlayQueue = onPlayQueue,
                onClearHistory = viewModel::clearHistory,
            )

            else -> LocalTab(
                tracks = localTracks,
                onPlayQueue = onPlayQueue,
                onImport = ::requestLocalImport,
                onClear = viewModel::clearLocalMusic,
            )
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<Track>,
    categories: List<String>,
    selectedCategory: String?,
    defaultCategory: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectCategory: (String?) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onRemoveFavorite: (Track) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    if (categories.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("全部") },
                )
            }
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category) },
                )
            }
        }
    }

    if (favorites.isEmpty()) {
        EmptyState(
            title = if (selectedCategory == null) "还没有收藏歌曲" else "这个收藏夹是空的",
            description = "在搜索结果或播放页收藏歌曲；默认收藏夹为“$defaultCategory”。",
            icon = Icons.Rounded.FavoriteBorder,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共 ${favorites.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (selectionMode) {
                    TextButton(onClick = onSelectAll) { Text("全选") }
                    TextButton(onClick = { onSelectionModeChange(false) }) { Text("取消") }
                } else {
                    TextButton(onClick = { onPlayQueue(favorites, 0) }) { Text("播放全部") }
                    TextButton(onClick = { onSelectionModeChange(true) }) { Text("选择") }
                }
            }
        }
        itemsIndexed(favorites, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                onClick = {
                    if (selectionMode) onToggleSelection(track.id)
                    else onPlayQueue(favorites, index)
                },
                isFavorite = true,
                onToggleFavorite = { onRemoveFavorite(track) },
                selected = if (selectionMode) track.id in selectedIds else null,
            )
        }
        if (selectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选 ${selectedIds.size} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDownloadSelected,
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text("下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<Track>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = "还没有播放记录",
            description = "播放过的歌曲会自动记录在这里。",
            icon = Icons.Rounded.History,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近播放 ${history.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearHistory) { Text("清空记录") }
            }
        }
        itemsIndexed(history, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                onClick = { onPlayQueue(history, index) },
            )
        }
    }
}

@Composable
private fun LocalTab(
    tracks: List<Track>,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (tracks.isEmpty()) "尚未导入本地音乐" else "本地音乐 ${tracks.size} 首",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onImport) { Text("导入") }
        if (tracks.isNotEmpty()) {
            TextButton(onClick = onClear) { Text("清空记录") }
        }
    }

    if (tracks.isEmpty()) {
        EmptyState(
            title = "导入本地音乐",
            description = "扫描系统媒体库中的音频文件，不会移动或修改原始文件。",
            icon = Icons.Rounded.LibraryMusic,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                onClick = { onPlayQueue(tracks, index) },
            )
        }
    }
}
