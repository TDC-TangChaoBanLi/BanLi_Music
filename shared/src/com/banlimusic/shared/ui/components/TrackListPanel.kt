/**
 * 文件作用：把列表头部、A-Z 索引、拖拽多选与底部操作栏组合成可复用列表面板。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banlimusic.app.domain.sort.AlphabetSection
import com.banlimusic.app.domain.sort.TrackSort

/**
 * One parameterized track list. The caller supplies the items, the header
 * configuration, the row content and the optional selection bar; header
 * collapsing, drag selection, A-Z navigation, empty state and footer are
 * implemented once here.
 */
@Composable
fun <T> TrackListPanel(
    items: List<T>,
    itemKey: (T) -> Any,
    validKey: (T) -> String,
    spec: TrackListHeaderSpec,
    listState: LazyListState,
    sections: List<AlphabetSection>,
    ascending: Boolean,
    selectionMode: Boolean,
    onStartSelection: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    immediateDragSelection: Boolean = false,
    onCategory: (String?) -> Unit = {},
    countActions: @Composable RowScope.() -> Unit = {},
    categoryTrailing: (@Composable RowScope.() -> Unit)? = null,
    selectionBar: @Composable BoxScope.() -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    row: @Composable (Int, T) -> Unit,
) {
    val validKeys = remember(items) { items.map(validKey).toSet() }
    val selectionModeState = rememberUpdatedState(selectionMode)
    val onStartSelectionState = rememberUpdatedState(onStartSelection)
    val onSelectState = rememberUpdatedState(onSelect)
    val selectHandler = remember {
        { id: String ->
            if (!selectionModeState.value) onStartSelectionState.value()
            onSelectState.value(id)
        }
    }
    // Keep the same pointer-input node while selection mode changes; recreating
    // it mid-gesture cancelled Android's long-press drag after the first row.
    val dragModifier = remember(listState, validKeys, immediateDragSelection) {
        if (immediateDragSelection) {
            Modifier.dragSelectImmediate(
                enabled = true,
                listState = listState,
                validKeys = validKeys,
                onSelect = selectHandler,
            )
        } else {
            Modifier.dragSelect(
                enabled = true,
                listState = listState,
                validKeys = validKeys,
                requireLongPress = true,
                onSelect = selectHandler,
            )
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        TrackListWithHeader(
            spec = spec,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            listModifier = dragModifier,
            bottomPadding = if (selectionMode) 64.dp else 20.dp,
            onSelectAll = onSelectAll,
            onCancelSelection = onCancelSelection,
            onSort = onSort,
            onToggleOrder = onToggleOrder,
            onCategory = onCategory,
            countActions = countActions,
            categoryTrailing = categoryTrailing,
        ) {
            if (items.isEmpty()) {
                item(key = "track-list-empty") { emptyContent() }
            } else {
                itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                    row(index, item)
                }
                if (footer != null) {
                    item(key = "track-list-footer") { footer() }
                }
            }
        }
        AlphabetOverlay(
            sections = sections,
            ascending = ascending,
            listState = listState,
            modifier = Modifier.matchParentSize(),
        )
        if (selectionMode) selectionBar()
    }
}
