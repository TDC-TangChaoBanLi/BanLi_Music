/**
 * 文件作用：提供按曲目选择优先播放来源的对话框。
 */
package com.banlimusic.app.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.ui.components.NON_PROVIDER_SOURCE_IDS
import com.banlimusic.app.ui.components.sourceDisplayName

/**
 * Lists every provider source of a merged track. Selecting one only changes
 * which source is tried first for playback, lyrics and artwork; it does not
 * fetch different metadata from the provider.
 */
@Composable
fun SourcePickerDialog(
    track: Track,
    preferredProviderId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sources = track.sources.filterNot { it.providerId in NON_PROVIDER_SOURCE_IDS }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择播放来源") },
        text = {
            Column {
                SourceOptionRow(
                    label = "自动（按合并顺序）",
                    detail = null,
                    selected = preferredProviderId == null,
                    onClick = { onSelect(null) },
                )
                sources.forEach { source ->
                    SourceOptionRow(
                        label = sourceDisplayName(source.providerId),
                        detail = source.qualityKbps?.let { "$it K" },
                        selected = source.providerId == preferredProviderId,
                        onClick = { onSelect(source.providerId) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun SourceOptionRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
