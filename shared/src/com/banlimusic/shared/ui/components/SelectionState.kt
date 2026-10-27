/**
 * 文件作用：两端共用的轻量多选状态与新建收藏夹对话框。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Screen-local multi-select state shared by desktop list screens. */
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
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
