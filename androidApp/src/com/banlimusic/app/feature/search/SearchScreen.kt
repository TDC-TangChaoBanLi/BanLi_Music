package com.banlimusic.app.feature.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.EmptyState
import com.banlimusic.app.ui.components.ErrorCard
import com.banlimusic.app.ui.components.SectionHeader
import com.banlimusic.app.ui.components.TrackRow
import com.banlimusic.app.ui.components.dragSelect
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.ui.components.qualityShortLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onPlayQueue: (List<Track>, Int) -> Unit,
    onExitApp: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    BackHandler { onExitApp() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val enabledSources by viewModel.enabledSources.collectAsStateWithLifecycle()
    val favoriteCategories by viewModel.favoriteCategories.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultFavoriteCategory.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val validIds = remember(state.results) { state.results.map { it.id }.toSet() }
    var chosenCategory by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var chosenQuality by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.selectedIds) {
        qualities = viewModel.downloadQualities()
    }
    LaunchedEffect(qualities) {
        if (chosenQuality !in qualities) chosenQuality = qualities.firstOrNull()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("搜索歌曲、歌手或专辑") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空")
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.submit()
                            keyboard?.hide()
                        },
                        enabled = state.query.isNotBlank() && !state.isSearching,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.submit()
                    keyboard?.hide()
                },
            ),
        )

        if (state.query.isBlank()) {
            SearchLanding(
                history = history,
                enabledSources = enabledSources,
                onQueryClick = viewModel::useHistory,
                onClearHistory = viewModel::clearHistory,
            )
            return@Column
        }

        if (!state.hasSearched && !state.isSearching) {
            SearchPrompt(
                query = state.query,
                onSearch = {
                    viewModel.submit()
                    keyboard?.hide()
                },
            )
            return@Column
        }

        if (state.isSearching && state.results.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isSearching && state.results.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center),
                        strokeWidth = 3.dp,
                    )
                }

                state.error != null && state.results.isEmpty() -> {
                    ErrorCard(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::retry,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                state.results.isEmpty() -> {
                    EmptyState(
                        title = "没有找到相关歌曲",
                        description = "换个关键词试试，或在设置中启用更多音乐源。",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.dragSelect(
                            enabled = true,
                            listState = listState,
                            validKeys = validIds,
                            onSelect = { id ->
                                if (!state.selectionMode) viewModel.setSelectionMode(true)
                                viewModel.toggleSelection(id)
                            },
                        ),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
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
                                    text = if (state.selectionMode) {
                                        "已选 ${state.selectedIds.size} 首"
                                    } else {
                                        "找到 ${state.results.size} 首"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                if (state.error != null) {
                                    Text(
                                        text = "部分来源加载失败",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (!state.selectionMode) {
                                    TextButton(onClick = { viewModel.setSelectionMode(true) }) {
                                        Text("选择")
                                    }
                                }
                            }
                        }
                        itemsIndexed(
                            items = state.results,
                            key = { _, track -> track.id },
                        ) { index, track ->
                            TrackRow(
                                track = track,
                                onClick = {
                                    if (state.selectionMode) {
                                        viewModel.toggleSelection(track.id)
                                    } else {
                                        keyboard?.hide()
                                        onPlayQueue(state.results, index)
                                    }
                                },
                                isFavorite = track.id in state.favoriteIds,
                                downloaded = track.id in downloadedIds,
                                categoryLabel = state.favoriteFolders[track.id],
                                onToggleFavorite = if (state.selectionMode) {
                                    null
                                } else {
                                    { viewModel.toggleFavorite(track) }
                                },
                                selected = if (state.selectionMode) {
                                    track.id in state.selectedIds
                                } else {
                                    null
                                },
                            )
                        }
                        item {
                            if (state.hasMore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        TextButton(onClick = viewModel::loadMore) {
                                            Text("加载更多")
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "已经到底了",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.selectionMode) {
            SelectionActionBar(
                count = state.selectedIds.size,
                categories = (favoriteCategories + defaultCategory).distinct(),
                chosenCategory = chosenCategory,
                onCategoryChange = { chosenCategory = it },
                qualities = qualities,
                chosenQuality = chosenQuality,
                onQualityChange = { chosenQuality = it },
                onFavorite = { viewModel.batchFavorite(chosenCategory) },
                onDownload = { viewModel.batchDownload(chosenQuality) },
                onSelectAll = viewModel::selectAll,
                onCancel = viewModel::clearSelection,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPrompt(
    query: String,
    onSearch: () -> Unit,
) {
    EmptyState(
        title = "点击搜索",
        description = "已输入“$query”。确认内容后点击搜索按钮，应用不会自动请求接口。",
        icon = Icons.Rounded.Search,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SelectionActionBar(
    count: Int,
    categories: List<String>,
    chosenCategory: String,
    onCategoryChange: (String) -> Unit,
    qualities: List<Int>,
    chosenQuality: Int?,
    onQualityChange: (Int) -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
) {
    var categoryMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CompactTextButton(
                    text = "$chosenCategory ▾",
                    onClick = { categoryMenu = true },
                )
                DropdownMenu(
                    expanded = categoryMenu,
                    onDismissRequest = { categoryMenu = false },
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                categoryMenu = false
                                onCategoryChange(category)
                            },
                        )
                    }
                }
            }
            CompactTextButton(text = "收藏", onClick = onFavorite, enabled = count > 0)
            Spacer(Modifier.weight(1f))
            if (qualities.isNotEmpty()) {
                Box {
                    CompactTextButton(
                        text = "${chosenQuality?.let(::qualityShortLabel) ?: "标准"} ▾",
                        onClick = { qualityMenu = true },
                    )
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false },
                    ) {
                        qualities.forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(qualityLabel(quality)) },
                                onClick = {
                                    qualityMenu = false
                                    onQualityChange(quality)
                                },
                            )
                        }
                    }
                }
            }
            CompactTextButton(text = "下载", onClick = onDownload, enabled = count > 0)
            CompactTextButton(text = "全选", onClick = onSelectAll, enabled = count > 0)
            CompactTextButton(text = "取消", onClick = onCancel)
        }
    }
}

@Composable
private fun CompactTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(text = text, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    history: List<String>,
    enabledSources: Set<String>,
    onQueryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        if (history.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearHistory) { Text("清空") }
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                history.forEach { query ->
                    AssistChip(
                        onClick = { onQueryClick(query) },
                        label = { Text(query) },
                    )
                }
            }
        } else {
            EmptyState(
                title = "搜索音乐",
                description = "输入歌名、歌手或专辑，结果会自动合并并去重。",
                icon = Icons.Rounded.Search,
            )
        }

        Spacer(Modifier.height(24.dp))
        val names = SUPPORTED_SOURCES.filter { it.id in enabledSources }.joinToString("、") { it.label }
        if (names.isNotBlank()) {
            Text(
                text = "已启用音源：$names",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "输入完成后点击搜索或键盘搜索键；应用不会自动发起请求。GD 音乐台限制每 5 分钟 50 次请求。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
