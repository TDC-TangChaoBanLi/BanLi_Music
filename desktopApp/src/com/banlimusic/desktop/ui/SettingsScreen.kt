package com.banlimusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.desktop.DesktopPaths
import com.banlimusic.desktop.data.DesktopDownloadStatus
import com.banlimusic.desktop.data.DesktopSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.roundToInt

@Composable
fun DesktopSettingsScreen(state: DesktopAppState) {
    val settings by state.settings.collectAsState()
    val favorites by state.favorites.collectAsState()
    val history by state.history.collectAsState()
    val searches by state.searchHistory.collectAsState()
    val downloads by state.downloads.collectAsState()
    val localTracks by state.localTracks.collectAsState()
    val repository = state.container.settingsRepository
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf(0L) }
    var showAgreement by remember { mutableStateOf(false) }
    var confirmClearFavorites by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val completedDownloads = downloads.count {
        it.status == DesktopDownloadStatus.COMPLETED
    }

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) {
            state.container.metadataCache.sizeBytes()
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp)) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "桌面端设置保存在系统用户配置中，来源与共享核心保持一致。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        SettingsCard(title = "外观", glyph = "◐") {
            Text("主题", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { repository.setThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "GD 音乐台（播放源）", glyph = "♫") {
            SUPPORTED_SOURCES.forEach { source ->
                SettingsToggleRow(
                    title = source.label,
                    description = "来源代码：${source.id}",
                    checked = settings.enabledSources.contains(source.id),
                    onCheckedChange = { enabled ->
                        repository.setSourceEnabled(source.id, enabled)
                    },
                )
            }
            Text(
                text = "至少保留一个来源。所有来源共享 5 分钟请求窗口和至少 1 秒的请求间隔。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "播放", glyph = "▶") {
            Text("播放音质", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopSettingsRepository.DESKTOP_QUALITIES.forEach { quality ->
                    FilterChip(
                        selected = settings.qualityKbps == quality,
                        onClick = { repository.setPlaybackQuality(quality) },
                        label = { Text(qualityLabel(quality)) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "桌面播放器当前使用 JLayer 解码 MP3，因此暂不提供 740 无损音质。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "搜索与请求", glyph = "⌕") {
            SettingsValueLabel("每页结果数", "${settings.searchPageSize} 条")
            Slider(
                value = settings.searchPageSize.toFloat(),
                onValueChange = {
                    repository.setSearchPageSize(it.roundToInt().coerceIn(10, 50))
                },
                valueRange = 10f..50f,
                steps = 7,
            )
            Spacer(Modifier.height(4.dp))
            SettingsValueLabel("5 分钟请求上限", "${settings.apiLimitPerWindow} 次")
            Slider(
                value = settings.apiLimitPerWindow.toFloat(),
                onValueChange = {
                    repository.setApiLimit(it.roundToInt().coerceIn(5, 50))
                },
                valueRange = 5f..50f,
                steps = 8,
            )
            Text(
                text = "搜索需要手动点击或按 Enter 触发，不会随输入自动请求。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "下载", glyph = "↓") {
            Text("下载音质", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopSettingsRepository.DESKTOP_QUALITIES.forEach { quality ->
                    FilterChip(
                        selected = settings.downloadQualityKbps == quality,
                        onClick = { repository.setDownloadQuality(quality) },
                        label = { Text(qualityLabel(quality)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                title = "同时下载歌词",
                description = "保存为与歌曲同名的 .lrc 文件",
                checked = settings.downloadLyrics,
                onCheckedChange = repository::setDownloadLyrics,
            )
            SettingsToggleRow(
                title = "同时下载海报",
                description = "保存为与歌曲同名的 .jpg 文件",
                checked = settings.downloadArtwork,
                onCheckedChange = repository::setDownloadArtwork,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "下载目录：" + settings.downloadDirectory.ifBlank {
                    DesktopPaths.downloadDir.toString()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        chooseDirectory(
                            initial = settings.downloadDirectory.ifBlank {
                                DesktopPaths.downloadDir.toString()
                            },
                            title = "选择下载目录",
                        )?.let(repository::setDownloadDirectory)
                    },
                ) { Text("选择目录") }
                TextButton(onClick = { repository.setDownloadDirectory("") }) {
                    Text("恢复默认")
                }
                TextButton(
                    onClick = {
                        openInFileManager(
                            File(
                                settings.downloadDirectory.ifBlank {
                                    DesktopPaths.downloadDir.toString()
                                },
                            ),
                        )
                    },
                ) { Text("打开目录") }
            }
            Text(
                text = "已完成 $completedDownloads 首。下载文件不会计入海报/歌词缓存。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "缓存与海报", glyph = "▣") {
            SettingsToggleRow(
                title = "列表页请求海报",
                description = "关闭后列表只读取已缓存海报；播放栏始终加载当前歌曲海报。",
                checked = settings.requestListArtwork,
                onCheckedChange = repository::setRequestListArtwork,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "海报与歌词缓存：${formatBytes(cacheBytes)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            state.container.metadataCache.clear()
                            state.container.artworkLoader.clearMemory()
                            state.container.musicRepository.clearCaches()
                            cacheBytes = withContext(Dispatchers.IO) {
                                state.container.metadataCache.sizeBytes()
                            }
                        }
                    },
                ) {
                    Text("清除缓存")
                }
            }
            Text(
                text = "配置：${DesktopPaths.configDir}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "缓存：${DesktopPaths.cacheDir}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "本地音乐", glyph = "♫") {
            Text(
                text = "目录：" + settings.localMusicDirectory.ifBlank {
                    DesktopPaths.localMusicDir.toString()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        chooseDirectory(
                            initial = settings.localMusicDirectory.ifBlank {
                                DesktopPaths.localMusicDir.toString()
                            },
                            title = "选择本地音乐目录",
                        )?.let { directory ->
                            repository.setLocalMusicDirectory(directory)
                            state.scanLocalMusic(directory)
                        }
                    },
                ) { Text("选择目录并扫描") }
                TextButton(
                    onClick = {
                        state.scanLocalMusic(
                            settings.localMusicDirectory.ifBlank {
                                DesktopPaths.localMusicDir.toString()
                            },
                        )
                    },
                    enabled = !state.localScanning,
                ) { Text("重新扫描") }
                TextButton(
                    onClick = { state.clearLocalMusic() },
                    enabled = localTracks.isNotEmpty(),
                ) { Text("清空索引") }
            }
            if (state.localScanning) {
                Text("正在扫描本地音乐…", style = MaterialTheme.typography.labelSmall)
            }
            state.localScanError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "已索引 ${localTracks.size} 首。只记录文件路径，不复制或修改原文件。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "音乐库与收藏", glyph = "♫") {
            Text(
                text = "收藏 ${favorites.size} 首，最近播放 ${history.size} 首，搜索历史 ${searches.size} 条",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "收藏和播放记录保存在 ${DesktopPaths.libraryFile}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { confirmClearFavorites = true },
                    enabled = favorites.isNotEmpty(),
                ) { Text("清空收藏") }
                TextButton(
                    onClick = { confirmClearHistory = true },
                    enabled = history.isNotEmpty(),
                ) { Text("清空最近播放") }
                TextButton(
                    onClick = {
                        scope.launch { state.container.libraryRepository.clearSearchHistory() }
                    },
                    enabled = searches.isNotEmpty(),
                ) { Text("清空搜索历史") }
            }
        }
        Spacer(Modifier.height(12.dp))

        SettingsCard(title = "关于", glyph = "ⓘ") {
            Text(
                text = "BanLiMusic Desktop",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "桌面端当前支持首页、搜索、收藏与播放记录、下载、MP3 播放、歌词、" +
                    "海报缓存、主题和来源设置。本地音乐扫描与自动更新将在后续阶段接入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAgreement = true }) {
                Text("重新阅读用户协议与 GD 音乐台声明")
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showAgreement) {
        AlertDialog(
            onDismissRequest = { showAgreement = false },
            title = { Text("用户协议与 GD 音乐台声明") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AgreementContent()
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgreement = false }) { Text("关闭") }
            },
        )
    }

    if (confirmClearFavorites) {
        AlertDialog(
            onDismissRequest = { confirmClearFavorites = false },
            title = { Text("清空收藏") },
            text = { Text("将删除全部 ${favorites.size} 首收藏，是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearFavorites = false
                        scope.launch { state.container.libraryRepository.clearFavorites() }
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFavorites = false }) { Text("取消") }
            },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("清空最近播放") },
            text = { Text("只清除播放记录，不影响收藏和已下载文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        scope.launch { state.container.libraryRepository.clearHistory() }
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("取消") }
            },
        )
    }
}

private fun chooseDirectory(initial: String, title: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        currentDirectory = File(initial).takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun openInFileManager(directory: File) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(directory.takeIf { it.isDirectory } ?: directory.parentFile)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    com.banlimusic.shared.ui.components.BanLiSettingsSwitchRow(
        title = title,
        description = description,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingsValueLabel(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun qualityLabel(kbps: Int): String = when (kbps) {
    128 -> "标准 128K"
    192 -> "较高 192K"
    320 -> "高品 320K"
    else -> "${kbps}K"
}
