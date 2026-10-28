/**
 * 文件作用：提供紧凑可配置的批量选择底部操作栏。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track

/** Optional trailing action, e.g. remove-from-favourites or delete. */
data class SelectionExtraAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * Shared, parameterized selection state. Every track list uses the same
 * quality lookup (falling back to the whole list when nothing is selected yet)
 * so the bar never changes shape when the user starts selecting.
 */
class SelectionActionsState(defaultCategory: String) {
    var chosenCategories by mutableStateOf(setOf(defaultCategory))
    var qualities by mutableStateOf<List<Int>>(emptyList())
    var chosenQuality by mutableStateOf<Int?>(null)
}

@Composable
fun rememberSelectionActionsState(
    selectionMode: Boolean,
    selectedIds: Set<String>,
    tracks: List<Track>,
    defaultCategory: String,
    downloadQualities: suspend (List<Track>) -> List<Int>,
): SelectionActionsState {
    val state = remember(defaultCategory) { SelectionActionsState(defaultCategory) }
    LaunchedEffect(selectionMode, selectedIds, tracks, defaultCategory) {
        if (!selectionMode) {
            state.qualities = emptyList()
            return@LaunchedEffect
        }
        val source = if (selectedIds.isEmpty()) {
            tracks
        } else {
            tracks.filter { it.id in selectedIds }
        }
        val available = downloadQualities(source)
        state.qualities = available
        if (state.chosenQuality !in available) state.chosenQuality = available.firstOrNull()
    }
    return state
}

/**
 * Bottom bar shown while selecting tracks. Font sizes follow the theme; only
 * the vertical padding and minimum touch size are reduced so the bar is dense.
 */
@Composable
fun TrackSelectionActionBar(
    categories: List<String>,
    state: SelectionActionsState,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    favoriteEnabled: Boolean = true,
    downloadEnabled: Boolean = true,
    extraAction: SelectionExtraAction? = null,
) {
    TrackSelectionActionBar(
        categories = categories,
        chosenCategories = state.chosenCategories,
        onCategoriesChange = { state.chosenCategories = it },
        onFavorite = onFavorite,
        onDownload = onDownload,
        modifier = modifier,
        qualities = state.qualities,
        chosenQuality = state.chosenQuality,
        onQualityChange = { state.chosenQuality = it },
        favoriteEnabled = favoriteEnabled,
        downloadEnabled = downloadEnabled,
        extraAction = extraAction,
    )
}

@Composable
fun TrackSelectionActionBar(
    categories: List<String>,
    chosenCategories: Set<String>,
    onCategoriesChange: (Set<String>) -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    qualities: List<Int> = emptyList(),
    chosenQuality: Int? = null,
    onQualityChange: (Int) -> Unit = {},
    favoriteEnabled: Boolean = true,
    downloadEnabled: Boolean = true,
    extraAction: SelectionExtraAction? = null,
) {
    var categoryMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(
                        onClick = { categoryMenu = true },
                        modifier = Modifier.heightIn(min = 28.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = folderSelectionLabel(chosenCategories),
                            maxLines = 1,
                            softWrap = false,
                            // Wide enough for a four-character folder name; longer
                            // names scroll instead of being cut off.
                            modifier = Modifier
                                .widthIn(max = 96.dp)
                                .basicMarquee(),
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenu,
                        onDismissRequest = { categoryMenu = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                leadingIcon = {
                                    Checkbox(
                                        checked = category in chosenCategories,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    onCategoriesChange(
                                        if (category in chosenCategories) {
                                            chosenCategories - category
                                        } else {
                                            chosenCategories + category
                                        },
                                    )
                                },
                            )
                        }
                        if (categories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无收藏夹") },
                                onClick = { categoryMenu = false },
                                enabled = false,
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("完成") },
                            onClick = { categoryMenu = false },
                        )
                    }
                }
                SelectionIconTextButton(
                    icon = Icons.Rounded.FavoriteBorder,
                    label = "收藏",
                    onClick = onFavorite,
                    enabled = favoriteEnabled,
                )
                if (qualities.isNotEmpty()) {
                    BarDivider()
                    Box {
                        TextButton(
                            onClick = { qualityMenu = true },
                            modifier = Modifier.heightIn(min = 28.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = "${chosenQuality?.let(::qualityShortLabel) ?: "标准"} ▾",
                                maxLines = 1,
                            )
                        }
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
                SelectionIconTextButton(
                    icon = Icons.Rounded.Download,
                    label = "下载",
                    onClick = onDownload,
                    enabled = downloadEnabled,
                )
                Spacer(Modifier.weight(1f))
                if (extraAction != null) {
                    BarDivider()
                    SelectionIconTextButton(
                        icon = extraAction.icon,
                        label = extraAction.label,
                        onClick = extraAction.onClick,
                        enabled = extraAction.enabled,
                    )
                }
            }
        }
    }

}

/** Compact icon + label button used by selection bars and compact list actions. */
@Composable
fun SelectionIconTextButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 28.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text(text = label, maxLines = 1)
        }
    }
}

@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Compact label: one folder name, or a count when several are selected. */
private fun folderSelectionLabel(selected: Set<String>): String = when {
    selected.isEmpty() -> "收藏夹 ▾"
    selected.size == 1 -> "${selected.first()} ▾"
    else -> "${selected.size} 个收藏夹 ▾"
}
