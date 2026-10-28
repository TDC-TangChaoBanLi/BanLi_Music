/**
 * 文件作用：两端共用的播放页元信息、来源徽标、收藏/下载操作与新建收藏夹对话框。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import com.banlimusic.app.core.model.Track

/**
 * Source chips + optional preferred-source line, identical on both platforms.
 */
@Composable
fun BanLiPlayerMetadata(
    track: Track,
    preferredProviderId: String? = null,
    providerCount: Int = track.sources.count {
        it.providerId != "local" && it.providerId != "download"
    },
    onSourceClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            track.primarySource?.let { source ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = sourceDisplayNameAnnotated(source.providerId),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
            if (providerCount > 1) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = if (onSourceClick != null) {
                        Modifier.clickable(onClick = onSourceClick)
                    } else {
                        Modifier
                    },
                ) {
                    Text(
                        text = "$providerCount 个来源",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                    )
                }
            }
            extra?.invoke()
        }
        if (preferredProviderId != null) {
            Text(
                text = "已选择来源：${sourceDisplayName(preferredProviderId)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Favorite/folder/download actions with the same menus on both platforms.
 */
@Composable
fun BanLiPlayerActions(
    isFavorite: Boolean,
    favoriteCategories: List<String>,
    canDownload: Boolean,
    downloadQualities: List<Int>,
    chosenQuality: Int?,
    onToggleFavorite: () -> Unit,
    onAddToCategory: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onDownload: () -> Unit,
    onDownloadQualityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var favoriteMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    val categories = favoriteCategories.distinct()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(if (isFavorite) "已收藏" else "收藏")
            }
            Box {
                IconButton(onClick = { favoriteMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "选择收藏夹",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = favoriteMenu,
                    onDismissRequest = { favoriteMenu = false },
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                favoriteMenu = false
                                onAddToCategory(category)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("新建收藏夹…") },
                        onClick = {
                            favoriteMenu = false
                            showNewCategory = true
                        },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onDownload, enabled = canDownload) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("下载")
            }
            if (downloadQualities.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = { qualityMenu = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "选择下载音质",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false },
                    ) {
                        downloadQualities.forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(qualityLabel(quality)) },
                                onClick = {
                                    qualityMenu = false
                                    onDownloadQualityChange(quality)
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = if (downloadQualities.isEmpty()) {
                "下载音质：默认（该来源不提供选择）"
            } else {
                "下载音质：${chosenQuality?.let(::qualityLabel) ?: "默认"}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showNewCategory) {
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("新建收藏夹") },
            text = {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("收藏夹名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCategory.isNotBlank(),
                    onClick = {
                        val name = newCategory.trim()
                        newCategory = ""
                        showNewCategory = false
                        onAddCategory(name)
                    },
                ) { Text("收藏") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategory = false }) { Text("取消") }
            },
        )
    }
}
