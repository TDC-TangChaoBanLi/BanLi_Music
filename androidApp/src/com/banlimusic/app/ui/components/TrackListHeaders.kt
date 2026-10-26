/**
 * 文件作用：提供可复用的列表头部插件（数量行、排序行、二级分类行）与折叠逻辑。
 */
package com.banlimusic.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.banlimusic.app.domain.sort.TrackSort
import kotlin.math.roundToInt

private const val POSITION_SCALE = 1_000_000
private const val LIST_HEADER_SPACER_KEY = "list-header-spacer"

/** The collapsing header occupies one spacer item at the top of the list. */
const val TRACK_LIST_HEADER_ITEM_COUNT = 1

/**
 * Declarative description of a track list header. The item order is fixed by
 * product requirements: count row (fixed) → sort row → secondary categories.
 */
data class TrackListHeaderSpec(
    val countText: String,
    val selectionMode: Boolean = false,
    val selectedCount: Int = 0,
    val sort: TrackSort? = null,
    val sortOptions: List<TrackSort> = emptyList(),
    val ascending: Boolean = true,
    /** Null hides the secondary category row entirely. */
    val categories: List<String>? = null,
    val selectedCategory: String? = null,
    val allCategoryLabel: String = "全部",
)

/**
 * Draws the count row on top of the list and slides the sort/category panel up
 * on downward scrolling. The panel expands again on any upward scroll. Both the
 * list padding and the panel height are measured once, so animating the header
 * never resizes the list (which caused the list to stop following the finger).
 */
@Composable
fun TrackListWithHeader(
    spec: TrackListHeaderSpec,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    horizontalStart: Dp = 12.dp,
    horizontalEnd: Dp = 30.dp,
    bottomPadding: Dp = 20.dp,
    onSelectAll: () -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onSort: (TrackSort) -> Unit = {},
    onToggleOrder: () -> Unit = {},
    onCategory: (String?) -> Unit = {},
    countActions: @Composable RowScope.() -> Unit = {},
    categoryTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    var countHeightPx by remember { mutableStateOf(0) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var collapsed by remember { mutableStateOf(false) }
    var lastPosition by remember { mutableStateOf(0 to 0) }
    val collapsePx by animateFloatAsState(
        targetValue = if (collapsed) panelHeightPx.toFloat() else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "track-header-collapse",
    )

    LaunchedEffect(listState) {
        // Position deltas are more reliable than isScrollInProgress, which turns
        // true before the first pixel moves and would collapse on a plain touch.
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val previous = lastPosition
                val delta = (index - previous.first) * POSITION_SCALE + (offset - previous.second)
                // Only hide the panel once the list has scrolled past it, so the
                // vacated area is already filled by rows instead of showing a gap.
                val pastPanel = index > 0 || offset >= panelHeightPx
                when {
                    delta > 0 && pastPanel -> collapsed = true
                    delta < 0 -> collapsed = false
                }
                lastPosition = index to offset
            }
    }

    val headerHeight = with(density) { (countHeightPx + panelHeightPx).toDp() }
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = listModifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalStart,
                end = horizontalEnd,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // A spacer item keeps the first row below the overlay header while
            // leaving item offsets free of content padding, so drag selection
            // still maps the finger to the row directly under it.
            item(key = LIST_HEADER_SPACER_KEY) {
                Spacer(Modifier.height(headerHeight))
            }
            content()
        }
        Column(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            ListCountRow(
                spec = spec,
                onSelectAll = onSelectAll,
                onCancelSelection = onCancelSelection,
                actions = countActions,
                modifier = Modifier.onSizeChanged { countHeightPx = it.height },
            )
            // clipToBounds keeps the sliding panel from drawing over the tab row
            // above; the vacated area reveals the list underneath.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                // The offset must be on the Surface: moving only its content
                // would leave the surface background behind as a blank strip.
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, -collapsePx.roundToInt()) }
                        .onSizeChanged { panelHeightPx = it.height },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (spec.sort != null && spec.sortOptions.isNotEmpty()) {
                            SortBar(
                                sort = spec.sort,
                                options = spec.sortOptions,
                                ascending = spec.ascending,
                                onSort = onSort,
                                onToggleOrder = onToggleOrder,
                            )
                        }
                        if (spec.categories != null) {
                            CategoryRow(
                                categories = spec.categories,
                                selectedCategory = spec.selectedCategory,
                                allLabel = spec.allCategoryLabel,
                                onCategory = onCategory,
                                trailing = categoryTrailing,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Sticky count row for lists that do not use the collapsing overlay header. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.trackListHeader(
    spec: TrackListHeaderSpec,
    onSelectAll: () -> Unit = {},
    onCancelSelection: () -> Unit = {},
    countActions: @Composable RowScope.() -> Unit = {},
) {
    stickyHeader(key = "list-header-count") {
        ListCountRow(
            spec = spec,
            onSelectAll = onSelectAll,
            onCancelSelection = onCancelSelection,
            actions = countActions,
        )
    }
}

@Composable
fun ListCountRow(
    spec: TrackListHeaderSpec,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(start = 12.dp, end = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (spec.selectionMode) {
                        "已选 ${spec.selectedCount} 首"
                    } else {
                        spec.countText
                    },
                    // Fill remaining space so the actions are always flush right.
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (spec.selectionMode) {
                    CompactTextButton(text = "全选", onClick = onSelectAll)
                    CompactTextButton(text = "取消", onClick = onCancelSelection)
                } else {
                    actions()
                }
            }
        }
    }
}

@Composable
fun SortBar(
    sort: TrackSort,
    options: List<TrackSort>,
    ascending: Boolean,
    onSort: (TrackSort) -> Unit,
    onToggleOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .padding(start = 12.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "排序",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { onSort(option) },
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .padding(end = 4.dp),
                    )
                }
            }
            IconButton(onClick = onToggleOrder, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (ascending) {
                        Icons.Rounded.ArrowUpward
                    } else {
                        Icons.Rounded.ArrowDownward
                    },
                    contentDescription = if (ascending) "升序" else "降序",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun CategoryRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "全部",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "category-all") {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategory(null) },
                    label = { Text(allLabel, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.heightIn(min = 28.dp),
                )
            }
            items(categories, key = { it }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategory(category) },
                    label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.heightIn(min = 28.dp),
                )
            }
            if (trailing != null) {
                item(key = "category-trailing") {
                    Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
                }
            }
        }
    }
}

/** Compact text button used by toolbars; keeps the theme font size. */
@Composable
fun CompactTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 28.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(text, maxLines = 1)
        }
    }
}

/** Shared tab label so the library and download tab rows use the same size. */
@Composable
fun ListTabLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/** Shared page header so both top-level screens align titles and tabs. */
@Composable
fun TrackScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
        )
        trailing()
    }
}
