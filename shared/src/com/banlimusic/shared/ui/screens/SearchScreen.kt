/**
 * 文件作用：两端共用的搜索页布局，包含搜索框、历史、分页结果与空错误状态。
 */
package com.banlimusic.shared.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiErrorCard

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val history: List<String> = emptyList(),
    val enabledSourceNames: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanLiSearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    searchIcon: ImageVector? = null,
    clearIcon: ImageVector? = null,
    searchFieldModifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: (LazyListScope.() -> Unit)? = null,
    selectionBar: (@Composable () -> Unit)? = null,
    trackRow: @Composable (Track, Int, Boolean) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BanLiSearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            loading = state.loading,
            modifier = searchFieldModifier,
            searchIcon = searchIcon,
            clearIcon = clearIcon,
        )

        if (state.query.isBlank()) {
            SearchLanding(
                history = state.history,
                enabledSourceNames = state.enabledSourceNames,
                onHistoryClick = onHistoryClick,
                onClearHistory = onClearHistory,
            )
            return@Column
        }

        if (!state.hasSearched && !state.loading) {
            BanLiEmptyState(
                title = "点击搜索",
                description = "已输入“${state.query}”。确认内容后点击搜索按钮，应用不会自动请求接口。",
                icon = searchIcon,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            return@Column
        }

        if (state.loading && state.results.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.loading && state.results.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center),
                        strokeWidth = 3.dp,
                    )
                }

                state.error != null && state.results.isEmpty() -> {
                    BanLiErrorCard(
                        message = state.error,
                        onRetry = onRetry,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                state.results.isEmpty() -> {
                    BanLiEmptyState(
                        title = "没有找到相关歌曲",
                        description = "换个关键词试试，或在设置中启用更多音乐源。",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.error != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        BanLiErrorCard(
                            message = state.error,
                            onRetry = onRetry,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        ResultList(
                            state = state,
                            listModifier = listModifier,
                            listState = listState,
                            header = header,
                            onLoadMore = onLoadMore,
                            trackRow = trackRow,
                        )
                    }
                }

                else -> {
                    ResultList(
                        state = state,
                        listModifier = listModifier,
                        listState = listState,
                        header = header,
                        onLoadMore = onLoadMore,
                        trackRow = trackRow,
                    )
                }
            }
        }

        if (state.selectionMode && selectionBar != null) {
            selectionBar()
        }
    }
}

@Composable
private fun ResultList(
    state: SearchUiState,
    listModifier: Modifier,
    listState: LazyListState,
    header: (LazyListScope.() -> Unit)?,
    onLoadMore: () -> Unit,
    trackRow: @Composable (Track, Int, Boolean) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = listModifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(
            items = state.results,
            key = { _, track -> track.id },
        ) { index, track ->
            trackRow(track, index, track.id in state.selectedIds)
        }
        item {
            if (state.hasMore) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = onLoadMore) { Text("加载更多") }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BanLiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
    searchIcon: ImageVector? = null,
    clearIcon: ImageVector? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        placeholder = { Text("搜索歌曲、歌手或专辑") },
        leadingIcon = searchIcon?.let {
            { Icon(it, contentDescription = null) }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        if (clearIcon != null) {
                            Icon(clearIcon, contentDescription = "清空")
                        } else {
                            Text("×")
                        }
                    }
                }
                IconButton(
                    onClick = {
                        onSubmit()
                        keyboard?.hide()
                    },
                    enabled = query.isNotBlank() && !loading,
                ) {
                    if (searchIcon != null) {
                        Icon(searchIcon, contentDescription = "搜索")
                    } else {
                        Text("搜索")
                    }
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSubmit()
                keyboard?.hide()
            },
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    history: List<String>,
    enabledSourceNames: String,
    onHistoryClick: (String) -> Unit,
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
                        onClick = { onHistoryClick(query) },
                        label = { Text(query) },
                    )
                }
            }
        } else {
            BanLiEmptyState(
                title = "搜索音乐",
                description = "输入歌名、歌手或专辑，结果会自动合并并去重。",
            )
        }

        Spacer(Modifier.height(24.dp))
        if (enabledSourceNames.isNotBlank()) {
            Text(
                text = "已启用音源：$enabledSourceNames",
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
