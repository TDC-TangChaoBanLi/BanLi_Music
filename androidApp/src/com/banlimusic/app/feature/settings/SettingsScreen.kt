package com.banlimusic.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banlimusic.app.core.common.formatDuration
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.qualityLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val rateStatus by viewModel.rateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            SettingsSection(title = "外观") {
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                )
                            },
                        )
                    }
                }
                SwitchRow(
                    title = "动态取色",
                    description = "Android 12 及以上跟随系统壁纸配色",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        }

        item {
            SettingsSection(title = "播放") {
                Text(
                    text = "音质偏好",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(128, 192, 320, 740, 999).forEach { quality ->
                        FilterChip(
                            selected = settings.qualityKbps == quality,
                            onClick = { viewModel.setQuality(quality) },
                            label = { Text(qualityLabel(quality)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "实际音质由音乐源决定；源不支持时会自动降级。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "允许移动网络播放",
                    description = "关闭后仅在 Wi-Fi 下播放和下载，搜索不受影响",
                    checked = settings.allowMobileNetwork,
                    onCheckedChange = viewModel::setAllowMobileNetwork,
                )
                SwitchRow(
                    title = "失败时自动切换来源",
                    description = "获取播放地址失败时优先尝试其他来源",
                    checked = settings.autoSwitchSource,
                    onCheckedChange = viewModel::setAutoSwitchSource,
                )
            }
        }

        item {
            SettingsSection(title = "音乐源") {
                Text(
                    text = "GD 音乐台当前开放：netease、joox、bilibili。启用多个来源时搜索结果会自动合并去重。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SUPPORTED_SOURCES.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = source.id in settings.enabledSources,
                            onCheckedChange = { checked ->
                                viewModel.setSourceEnabled(source.id, checked)
                            },
                        )
                        Text(
                            text = source.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = "请求状态") {
                val status = rateStatus
                if (status == null) {
                    Text(
                        text = "还没有请求记录，或当前来源不提供状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "5 分钟窗口内剩余 ${status.remaining} / ${status.limit} 次请求",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (status.cooldownRemainingMs > 0) {
                            "服务端要求冷却，约 ${formatDuration(status.cooldownRemainingMs / 1000)}后可重试。"
                        } else if (status.windowResetInMs > 0) {
                            "约 ${formatDuration(status.windowResetInMs / 1000)}后释放一个请求额度。"
                        } else {
                            "当前没有请求限额占用。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = viewModel::refreshRateStatus) { Text("刷新状态") }
            }
        }

        item {
            SettingsSection(title = "数据") {
                TextButton(
                    onClick = {
                        viewModel.clearSearchHistory()
                        Toast.makeText(context, "已清空搜索历史", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("清空搜索历史")
                }
                TextButton(
                    onClick = {
                        viewModel.clearCaches()
                        Toast.makeText(context, "已清理搜索、歌词和播放地址缓存", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("清理缓存")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "来源与声明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "音乐数据来自 GD 音乐台（music.gdstudio.xyz），API 由 GD Studio 提供，遵循 CC BY-NC 4.0。本站资源仅限个人学习参考，请勿下载、传播或用于商业用途。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item {
            Text(
                text = "BanLiMusic $versionName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
