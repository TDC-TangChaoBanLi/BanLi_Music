package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banlimusic.desktop.data.DesktopSettingsRepository

/** Screen-local multi-select state. */
class TrackSelectionState {
    var mode by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    fun enter() {
        mode = true
        selectedIds = emptySet()
    }

    fun exit() {
        mode = false
        selectedIds = emptySet()
    }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun selectAll(ids: Collection<String>) {
        selectedIds = ids.toSet()
    }

    fun retain(ids: Set<String>) {
        selectedIds = selectedIds.intersect(ids)
    }
}

@Composable
fun rememberTrackSelectionState(): TrackSelectionState =
    remember { TrackSelectionState() }

@Composable
fun TrackSelectionBar(
    selection: TrackSelectionState,
    selectableIds: Collection<String>,
    categories: List<String>,
    onFavorite: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onDownload: (Int) -> Unit,
    initialQuality: Int,
    allowDownload: Boolean = true,
) {
    var folderMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    val count = selection.selectedIds.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "已选 $count / ${selectableIds.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { selection.selectAll(selectableIds) }) {
                Text("全选")
            }
            Box {
                TextButton(
                    onClick = { folderMenu = true },
                    enabled = count > 0,
                ) {
                    Text("收藏到…")
                }
                DropdownMenu(
                    expanded = folderMenu,
                    onDismissRequest = { folderMenu = false },
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                folderMenu = false
                                onFavorite(category)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("新建收藏夹…") },
                        onClick = {
                            folderMenu = false
                            onCreateCategory()
                        },
                    )
                }
            }
            if (allowDownload) {
                Box {
                    TextButton(
                        onClick = { qualityMenu = true },
                        enabled = count > 0,
                    ) {
                        Text("下载…")
                    }
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false },
                    ) {
                        DesktopSettingsRepository.DESKTOP_QUALITIES.forEach { quality ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${quality}K" +
                                            if (quality == initialQuality) "（默认）" else "",
                                    )
                                },
                                onClick = {
                                    qualityMenu = false
                                    onDownload(quality)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = selection::exit) {
                Text("取消选择")
            }
        }
    }
}

@Composable
fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建收藏夹") },
        text = {
            Column {
                Text(
                    text = "输入收藏夹名称，创建后可批量收藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true,
                    label = { Text("名称") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalized = name.trim()
                    if (normalized.isNotEmpty()) onCreate(normalized)
                },
                enabled = name.isNotBlank(),
            ) { Text("创建并收藏") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
