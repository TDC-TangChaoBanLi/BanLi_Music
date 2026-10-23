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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.banlimusic.app.core.datastore.WidgetTheme
import com.banlimusic.app.feature.common.AppViewModelProvider
import com.banlimusic.app.ui.components.formatBytes
import com.banlimusic.app.ui.components.qualityLabel
import com.banlimusic.app.widget.PlayerWidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val rateStatus by viewModel.rateStatus.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.2.0"
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
                SwitchRow(
                    title = "单曲页默认显示歌词",
                    description = "开启后左滑页面初始停在歌词页",
                    checked = settings.defaultShowLyrics,
                    onCheckedChange = viewModel::setDefaultShowLyrics,
                )
                LabeledSlider(
                    title = "失败重试次数",
                    valueText = "${settings.playbackRetryCount} 次",
                    value = settings.playbackRetryCount.toFloat(),
                    valueRange = 0f..10f,
                    steps = 9,
                ) { viewModel.setPlaybackRetryCount(it.toInt()) }
                LabeledSlider(
                    title = "连续失败停止阈值",
                    valueText = "${settings.consecutiveFailureLimit} 首",
                    value = settings.consecutiveFailureLimit.toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                ) { viewModel.setConsecutiveFailureLimit(it.toInt()) }
                Text(
                    text = "重试间隔依次为 2、4、8、16 秒；达到停止阈值后会发送系统通知并停止播放。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(title = "GD 音乐台") {
                Text(
                    text = "当前开放：netease、joox、bilibili。启用多个来源时搜索结果会自动合并去重。",
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "音源专属配置集中在此模块，后续新增来源会使用独立的设置区块。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    title = "5 分钟请求上限",
                    valueText = "${settings.apiLimitPerWindow} 次",
                    value = settings.apiLimitPerWindow.toFloat(),
                    valueRange = 1f..50f,
                    steps = 48,
                ) { viewModel.setApiLimit(it.toInt()) }
                Text(
                    text = "不得超过 50 次；GD 音乐台请求之间强制间隔至少 1 秒。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "每次搜索/加载数量", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { size ->
                        FilterChip(
                            selected = settings.searchPageSize == size,
                            onClick = { viewModel.setSearchPageSize(size) },
                            label = { Text("$size 首") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
            SettingsSection(title = "下载") {
                var downloadDirectory by remember(settings.downloadDirectory) {
                    mutableStateOf(settings.downloadDirectory)
                }
                OutlinedTextField(
                    value = downloadDirectory,
                    onValueChange = { downloadDirectory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("下载目录") },
                    placeholder = { Text("留空使用系统音乐/BanLiMusic") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { viewModel.setDownloadDirectory(downloadDirectory) }) {
                            Text("保存")
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "留空时优先使用系统音乐目录；Android 10 及以上如果目录不可写，会自动回退到应用外部音乐目录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "下载音质", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.downloadQualityKbps == 0,
                        onClick = { viewModel.setDownloadQuality(0) },
                        label = { Text("跟随播放音质") },
                    )
                    listOf(128, 192, 320, 740, 999).forEach { quality ->
                        FilterChip(
                            selected = settings.downloadQualityKbps == quality,
                            onClick = { viewModel.setDownloadQuality(quality) },
                            label = { Text(qualityLabel(quality)) },
                        )
                    }
                }
                SwitchRow(
                    title = "同时下载歌词",
                    description = "在音频文件旁保存同名 .lrc 文件",
                    checked = settings.downloadLyrics,
                    onCheckedChange = viewModel::setDownloadLyrics,
                )
                SwitchRow(
                    title = "同时下载海报",
                    description = "在音频文件旁保存同名 .jpg 文件",
                    checked = settings.downloadArtwork,
                    onCheckedChange = viewModel::setDownloadArtwork,
                )
            }
        }

        item {
            SettingsSection(title = "缓存") {
                Text(
                    text = "当前缓存：${formatBytes(cacheSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(text = "最大缓存大小", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        100L * 1024 * 1024,
                        200L * 1024 * 1024,
                        500L * 1024 * 1024,
                        1024L * 1024 * 1024,
                    ).forEach { bytes ->
                        FilterChip(
                            selected = settings.cacheMaxBytes == bytes,
                            onClick = { viewModel.setCacheMaxBytes(bytes) },
                            label = { Text(formatBytes(bytes)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        viewModel.clearCaches()
                        Toast.makeText(context, "已清理缓存", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("清理缓存") }
                TextButton(
                    onClick = {
                        viewModel.clearSearchHistory()
                        Toast.makeText(context, "已清空搜索历史", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("清空搜索历史") }
                TextButton(onClick = viewModel::refreshCacheSize) { Text("刷新缓存大小") }
            }
        }

        item {
            SettingsSection(title = "音乐库与收藏") {
                var defaultCategory by remember(settings.defaultFavoriteCategory) {
                    mutableStateOf(settings.defaultFavoriteCategory)
                }
                OutlinedTextField(
                    value = defaultCategory,
                    onValueChange = { defaultCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("默认收藏夹") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { viewModel.setDefaultFavoriteCategory(defaultCategory) }) {
                            Text("保存")
                        }
                    },
                )
                Text(
                    text = "播放页收藏按钮默认收藏到此文件夹；分类可在收藏页筛选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "启动时自动导入本地音乐",
                    description = "需要媒体读取权限；只记录系统媒体库中的音频，不复制文件",
                    checked = settings.autoImportLocalMusic,
                    onCheckedChange = viewModel::setAutoImportLocalMusic,
                )
            }
        }

        item {
            SettingsSection(title = "桌面小组件") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.widgetTheme == theme,
                            onClick = {
                                viewModel.setWidgetTheme(theme)
                                scope.launch {
                                    delay(200L)
                                    PlayerWidgetUpdater.refresh(context)
                                }
                            },
                            label = {
                                Text(
                                    when (theme) {
                                        WidgetTheme.SYSTEM -> "跟随系统"
                                        WidgetTheme.BLACK -> "黑色"
                                        WidgetTheme.WHITE -> "白色"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    text = "小组件支持 3x1/4x1 两种布局，可调整大小；磨砂效果使用半透明背景近似实现。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(title = "导航栏") {
                Text(
                    text = "选择显示的入口并调整顺序，至少保留一项。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                var navItems by remember(settings.bottomNavItems) {
                    mutableStateOf(settings.bottomNavItems)
                }
                val options = com.banlimusic.app.core.datastore.BOTTOM_NAV_OPTIONS
                options.forEach { option ->
                    val enabled = option.id in navItems
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val updated = if (checked) {
                                    navItems + option.id
                                } else if (navItems.size > 1) {
                                    navItems - option.id
                                } else {
                                    navItems
                                }
                                navItems = updated
                                viewModel.setBottomNavItems(updated)
                            },
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (enabled) {
                            val index = navItems.indexOf(option.id)
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    val updated = navItems.toMutableList()
                                    updated.removeAt(index)
                                    updated.add(index - 1, option.id)
                                    navItems = updated
                                    viewModel.setBottomNavItems(updated)
                                },
                            ) { Text("上移") }
                            TextButton(
                                enabled = index < navItems.lastIndex,
                                onClick = {
                                    val updated = navItems.toMutableList()
                                    updated.removeAt(index)
                                    updated.add(index + 1, option.id)
                                    navItems = updated
                                    viewModel.setBottomNavItems(updated)
                                },
                            ) { Text("下移") }
                        }
                    }
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
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = current,
        onValueChange = { current = it },
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = { onValueChangeFinished(current) },
    )
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
