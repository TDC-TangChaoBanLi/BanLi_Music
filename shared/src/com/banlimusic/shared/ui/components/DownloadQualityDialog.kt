/**
 * 文件作用：下载音质冲突确认弹窗，供两端批量下载与缓存转下载复用。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.DownloadQualityConflict
import com.banlimusic.app.core.model.DownloadQualityDecision

/** Compact view model for the dialog so both platforms pass plain data. */
data class DownloadQualityPrompt(
    val trackCount: Int,
    val existingQualities: List<Int>,
    val requestedQualities: List<Int>,
    val sampleTitles: List<String> = emptyList(),
)

fun downloadQualityPrompt(conflicts: List<DownloadQualityConflict>): DownloadQualityPrompt =
    DownloadQualityPrompt(
        trackCount = conflicts.size,
        existingQualities = conflicts
            .flatMap { it.existingQualities }
            .filter { it > 0 }
            .distinct()
            .sorted(),
        requestedQualities = conflicts
            .map { it.requestedQualityKbps }
            .filter { it > 0 }
            .distinct()
            .sorted(),
        sampleTitles = conflicts.map { it.track.title }.filter { it.isNotBlank() }.take(3),
    )

@Composable
fun BanLiDownloadQualityDialog(
    prompt: DownloadQualityPrompt,
    onDecision: (DownloadQualityDecision) -> Unit,
) {
    val existing = prompt.existingQualities.joinToString("、") { qualityLabel(it) }
    val requested = prompt.requestedQualities.joinToString("、") { qualityLabel(it) }
    AlertDialog(
        onDismissRequest = { onDecision(DownloadQualityDecision.SKIP) },
        title = { Text("已下载其他音质") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (prompt.trackCount == 1) {
                        "这首歌已经下载了其他音质，请选择处理方式。"
                    } else {
                        "所选的 ${prompt.trackCount} 首歌中已有其他音质，选择将应用到全部歌曲。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "已下载：$existing    本次：$requested",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (prompt.sampleTitles.isNotEmpty()) {
                    Text(
                        text = prompt.sampleTitles.joinToString("、") { "《$it》" } +
                            if (prompt.trackCount > prompt.sampleTitles.size) " 等" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { onDecision(DownloadQualityDecision.KEEP_HIGHEST) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保留高品音质")
                }
                OutlinedButton(
                    onClick = { onDecision(DownloadQualityDecision.REPLACE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("替换当前音质")
                }
                OutlinedButton(
                    onClick = { onDecision(DownloadQualityDecision.COEXIST) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("并存两个音质")
                }
                Text(
                    "并存时文件会全部保留，本地列表只显示最高音质。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(DownloadQualityDecision.SKIP) }) {
                Text("跳过下载")
            }
        },
    )
}
