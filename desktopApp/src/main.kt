/**
 * 文件作用：桌面端应用入口，负责单实例检查、托盘常驻与主窗口生命周期。
 */
package com.banlimusic.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.banlimusic.desktop.ui.BanLiDesktopTheme
import com.banlimusic.desktop.ui.DesktopAgreementScreen
import com.banlimusic.desktop.ui.DesktopAppState
import com.banlimusic.desktop.ui.DesktopDownloadsScreen
import com.banlimusic.desktop.ui.DesktopHomeScreen
import com.banlimusic.desktop.ui.DesktopLibraryScreen
import com.banlimusic.desktop.ui.DesktopNowPlayingScreen
import com.banlimusic.desktop.ui.DesktopPlayerBar
import com.banlimusic.desktop.ui.DesktopSearchScreen
import com.banlimusic.desktop.ui.DesktopFloatingLyrics
import com.banlimusic.desktop.ui.DesktopSettingsScreen
import com.banlimusic.desktop.ui.DesktopTab
import com.banlimusic.shared.resources.Res
import com.banlimusic.shared.resources.banli_logo
import com.banlimusic.shared.ui.components.BanLiDownloadQualityDialog
import com.banlimusic.shared.ui.components.BanLiProgressSection
import com.banlimusic.shared.ui.components.downloadQualityPrompt
import com.banlimusic.shared.ui.navigation.BanLiAppShell
import com.banlimusic.shared.ui.navigation.BanLiBarPosition
import com.banlimusic.shared.ui.navigation.BanLiDestination
import com.banlimusic.app.core.datastore.TabletMiniPlayerPosition
import com.banlimusic.app.core.datastore.TabletNavPosition
import com.banlimusic.desktop.player.DesktopMediaKeys
import com.banlimusic.desktop.player.DesktopPlayMode
import com.banlimusic.shared.ui.navigation.BanLiNavigationLayout
import com.banlimusic.shared.ui.navigation.BanLiNavigationSide
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

fun main() {
    // Let the JVM use the OS proxy settings; Microsoft endpoints are often
    // reachable only through the user's system proxy.
    System.setProperty("java.net.useSystemProxies", "true")
    when (val instance = DesktopSingleInstance.acquire()) {
        is DesktopSingleInstance.AcquireResult.Primary -> {
            try {
                launchDesktop(instance.instance)
            } finally {
                instance.instance.close()
            }
        }

        is DesktopSingleInstance.AcquireResult.Secondary -> {
            if (!DesktopSingleInstance.requestShow(instance.portFile)) {
                System.err.println("BanLiMusic 已在运行，但无法唤出已有窗口。")
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun launchDesktop(singleInstance: DesktopSingleInstance) = application {
    val container = remember { DesktopContainer() }
    DisposableEffect(container) {
        onDispose { container.close() }
    }
    val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
    val traySupported = remember { SystemTray.isSupported() }
    var windowVisible by remember { mutableStateOf(true) }
    var windowRef by remember { mutableStateOf<ComposeWindow?>(null) }
    var trayPopupVisible by remember { mutableStateOf(false) }
    var trayPopupX by remember { mutableIntStateOf(0) }
    var trayPopupY by remember { mutableIntStateOf(0) }
    var trayIconRef by remember { mutableStateOf<TrayIcon?>(null) }
    val appSettings by container.settingsRepository.settings.collectAsState()
    val playback by container.player.state.collectAsState()
    val favorites by container.libraryRepository.favorites.collectAsState()
    val scope = rememberCoroutineScope()

    fun showWindow() {
        trayPopupVisible = false
        windowVisible = true
        SwingUtilities.invokeLater {
            windowRef?.apply {
                if (extendedState and Frame.ICONIFIED != 0) {
                    extendedState = extendedState and Frame.ICONIFIED.inv()
                }
                isVisible = true
                toFront()
                requestFocus()
            }
        }
    }

    fun hideWindow() {
        trayPopupVisible = false
        if (traySupported) windowVisible = false else exitApplication()
    }

    // A second launch locks the same instance file, connects to this process and
    // asks it to restore the window instead of starting another tray icon.
    DisposableEffect(singleInstance) {
        singleInstance.onShowRequest = { showWindow() }
        onDispose { singleInstance.onShowRequest = null }
    }

    // AWT keeps the native tray behaviour, while the menu itself is a Compose
    // dialog so Chinese text is rendered by Skia and no taskbar button appears.
    DisposableEffect(traySupported) {
        if (!traySupported) return@DisposableEffect onDispose { }
        val tray = SystemTray.getSystemTray()
        val icon = TrayIcon(loadTrayImage(), "BanLiMusic").apply { isImageAutoSize = true }
        var lastPopupAt = 0L

        fun showPopup(event: MouseEvent) {
            val now = System.currentTimeMillis()
            if (now - lastPopupAt < 250L) return
            lastPopupAt = now
            val point = event.locationOnScreen
            val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            val popupWidth = 268
            val popupHeight = 400
            val rawX = if (point.x <= 2 || point.y <= 2) {
                bounds.x + bounds.width - popupWidth
            } else {
                point.x - popupWidth / 2
            }
            val rawY = if (point.x <= 2 || point.y <= 2) {
                bounds.y + bounds.height - popupHeight
            } else {
                point.y - popupHeight
            }
            trayPopupX = rawX.coerceIn(bounds.x, (bounds.x + bounds.width - popupWidth).coerceAtLeast(bounds.x))
            trayPopupY = rawY.coerceIn(bounds.y, (bounds.y + bounds.height - popupHeight).coerceAtLeast(bounds.y))
            trayPopupVisible = true
        }

        icon.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    if (event.isPopupTrigger || SwingUtilities.isRightMouseButton(event)) {
                        showPopup(event)
                    }
                }

                override fun mouseReleased(event: MouseEvent) {
                    when {
                        event.isPopupTrigger || SwingUtilities.isRightMouseButton(event) ->
                            showPopup(event)

                        SwingUtilities.isLeftMouseButton(event) -> showWindow()
                    }
                }
            },
        )
        icon.addActionListener { showWindow() }
        runCatching { tray.add(icon) }
        trayIconRef = icon
        onDispose {
            trayIconRef = null
            runCatching { tray.remove(icon) }
        }
    }

    LaunchedEffect(playback.track?.id, playback.playing) {
        val track = playback.track
        trayIconRef?.toolTip = if (track == null) {
            "BanLiMusic"
        } else {
            "${if (playback.playing) "▶" else "⏸"} ${track.title} - ${track.artistLine}"
        }
    }

    val mediaKeys = remember(container) { DesktopMediaKeys(container.player) }
    DisposableEffect(mediaKeys) {
        mediaKeys.start()
        onDispose { mediaKeys.close() }
    }

    if (trayPopupVisible) {
        val popupState = rememberDialogState(width = 268.dp, height = 400.dp)
        val track = playback.track
        val isFavorite = track != null && favorites.any { it.id == track.id }
        DialogWindow(
            onCloseRequest = { trayPopupVisible = false },
            state = popupState,
            title = "BanLiMusic",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            LaunchedEffect(trayPopupX, trayPopupY) {
                window.setLocation(trayPopupX, trayPopupY)
                withFrameNanos { }
                val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                val size = window.size
                window.setLocation(
                    trayPopupX.coerceIn(
                        bounds.x,
                        (bounds.x + bounds.width - size.width).coerceAtLeast(bounds.x),
                    ),
                    trayPopupY.coerceIn(
                        bounds.y,
                        (bounds.y + bounds.height - size.height).coerceAtLeast(bounds.y),
                    ),
                )
            }
            LaunchedEffect(Unit) {
                SwingUtilities.invokeLater {
                    window.toFront()
                    window.requestFocus()
                }
            }
            DisposableEffect(window) {
                val shownAt = System.currentTimeMillis()
                val listener = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(event: java.awt.event.WindowEvent) = Unit
                    override fun windowLostFocus(event: java.awt.event.WindowEvent) {
                        if (System.currentTimeMillis() - shownAt > 250L) {
                            trayPopupVisible = false
                        }
                    }
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            BanLiDesktopTheme(appSettings.themeMode, appSettings.customThemeColor) {
                DesktopTrayPopup(
                    container = container,
                    playback = playback,
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        if (track != null) {
                            scope.launch {
                                try {
                                    container.libraryRepository.toggleFavorite(track)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Throwable) {
                                    // The next tap retries; a failed write must not crash the tray.
                                }
                            }
                        }
                    },
                    onShowWindow = ::showWindow,
                    onExit = ::exitApplication,
                )
            }
        }
    }

    if (appSettings.floatingLyrics) {
        DesktopFloatingLyrics(
            container = container,
            settings = appSettings,
            onClose = { container.settingsRepository.setFloatingLyrics(false) },
        )
    }

    Window(
        visible = windowVisible,
        onCloseRequest = { hideWindow() },
        title = "BanLiMusic",
        icon = painterResource(Res.drawable.banli_logo),
        state = windowState,
        decoration = WindowDecoration.Undecorated(),
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(1_000, 680)
            windowRef = window
        }
        DesktopApp(
            container = container,
            windowState = windowState,
            onExit = ::exitApplication,
            onClose = { hideWindow() },
        )
    }
}

@Composable
private fun DesktopTrayPopup(
    container: DesktopContainer,
    playback: com.banlimusic.desktop.player.DesktopPlaybackState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShowWindow: () -> Unit,
    onExit: () -> Unit,
) {
    val track = playback.track
    val player = container.player
    val volume = playback.volume.coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 10.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = track?.title ?: "还没有播放歌曲",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.artistLine ?: "BanLiMusic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactTrayIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一曲",
                    enabled = playback.hasQueue,
                    onClick = player::previous,
                )
                Spacer(Modifier.width(14.dp))
                FilledIconButton(
                    onClick = player::togglePause,
                    enabled = track != null,
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (playback.playing) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playback.playing) "暂停" else "播放",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                CompactTrayIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "下一曲",
                    enabled = playback.hasQueue,
                    onClick = { player.next() },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactTrayIconButton(
                    icon = playback.playMode.trayIcon(),
                    contentDescription = playback.playMode.label,
                    enabled = true,
                    onClick = player::cyclePlayMode,
                )
                CompactTrayIconButton(
                    icon = if (isFavorite) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    enabled = track != null,
                    onClick = onToggleFavorite,
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            BanLiProgressSection(
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                onSeek = player::seekTo,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (volume <= 0.01f) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = "音量",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = volume,
                    onValueChange = player::setVolume,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "${(volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            TrayMenuAction(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                label = "显示窗口",
                onClick = onShowWindow,
            )
            TrayMenuAction(
                icon = Icons.Rounded.PowerSettingsNew,
                label = "退出",
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun CompactTrayIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

@Composable
private fun TrayMenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun DesktopPlayMode.trayIcon(): ImageVector = when (this) {
    DesktopPlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    DesktopPlayMode.SHUFFLE -> Icons.Rounded.Shuffle
    else -> Icons.Rounded.Repeat
}

/** Loads the shared logo from the composeResources jar for the AWT tray icon. */
private fun loadTrayImage(): java.awt.Image {
    val stream = object {}.javaClass.classLoader?.getResourceAsStream(
        "composeResources/com.banlimusic.shared.resources/drawable/banli_logo.png",
    )
    val source = runCatching { stream?.use { ImageIO.read(it) } }.getOrNull()
    val size = SystemTray.getSystemTray().trayIconSize
    if (source != null) {
        return source.getScaledInstance(
            size.width.coerceAtLeast(16),
            size.height.coerceAtLeast(16),
            java.awt.Image.SCALE_SMOOTH,
        )
    }
    return BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().apply {
            color = java.awt.Color(0x0F, 0x76, 0x6E)
            fillRect(0, 0, width, height)
            dispose()
        }
    }
}

@Composable
private fun WindowScope.DesktopApp(
    container: DesktopContainer,
    windowState: WindowState,
    onExit: () -> Unit,
    onClose: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsState()
    var maximized by remember {
        mutableStateOf(windowState.placement == WindowPlacement.Maximized)
    }

    fun toggleMaximize() {
        maximized = !maximized
        windowState.placement = if (maximized) {
            WindowPlacement.Maximized
        } else {
            WindowPlacement.Floating
        }
    }

    BanLiDesktopTheme(settings.themeMode, settings.customThemeColor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DesktopTitleBar(
                    maximized = maximized,
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = ::toggleMaximize,
                    onClose = onClose,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!settings.agreementAccepted) {
                        DesktopAgreementScreen(
                            onAccept = { container.settingsRepository.setAgreementAccepted(true) },
                            onExit = onExit,
                        )
                    } else {
                        DesktopMain(container)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowScope.DesktopTitleBar(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowDraggableArea(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.banli_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "BanLiMusic",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TitleBarButton(Icons.Rounded.Remove, "最小化", onMinimize)
            TitleBarButton(
                icon = if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
                description = if (maximized) "还原" else "最大化",
                onClick = onToggleMaximize,
            )
            TitleBarButton(Icons.Rounded.Close, "关闭", onClose)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TitleBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 46.dp, height = 40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DesktopMain(container: DesktopContainer) {
    val scope = rememberCoroutineScope()
    val state = remember(container, scope) { DesktopAppState(container, scope) }
    val settings by state.settings.collectAsState()
    LaunchedEffect(Unit) {
        state.container.syncRepository.maybeSyncOnStart()
    }
    val orderedTabs = remember(settings.bottomNavItems) {
        val order = settings.bottomNavItems.withIndex().associate { it.value to it.index }
        DesktopTab.entries.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }
    val destinations = remember(orderedTabs) {
        orderedTabs.map { tab ->
            BanLiDestination(route = tab.name, label = tab.label, icon = tab.icon)
        }
    }
    val playerBar: (@Composable () -> Unit)? = if (state.showPlayer) {
        null
    } else {
        { DesktopPlayerBar(state) }
    }

    BanLiAppShell(
        destinations = destinations,
        currentRoute = if (state.showPlayer) null else state.tab.name,
        onNavigate = { route ->
            orderedTabs.firstOrNull { it.name == route }?.let(state::selectTab)
        },
        navigationLayout = BanLiNavigationLayout.SIDE,
        showNavigation = !state.showPlayer,
        navigationSide = if (settings.tabletNavPosition == TabletNavPosition.RIGHT) {
            BanLiNavigationSide.END
        } else {
            BanLiNavigationSide.START
        },
        navigationContainerColor = MaterialTheme.colorScheme.surface,
        playerBar = playerBar,
        playerBarPosition = if (
            settings.tabletMiniPlayerPosition == TabletMiniPlayerPosition.TOP
        ) {
            BanLiBarPosition.TOP
        } else {
            BanLiBarPosition.BOTTOM
        },
        railHeader = {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "♫",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "BanLi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Music",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        },
        content = {
            if (state.showPlayer) {
                DesktopNowPlayingScreen(state)
            } else {
                when (state.tab) {
                    DesktopTab.HOME -> DesktopHomeScreen(state)
                    DesktopTab.SEARCH -> DesktopSearchScreen(state)
                    DesktopTab.LIBRARY -> DesktopLibraryScreen(state)
                    DesktopTab.DOWNLOADS -> DesktopDownloadsScreen(state)
                    DesktopTab.SETTINGS -> DesktopSettingsScreen(state)
                }
            }
        },
    )
    if (state.downloadConflicts.isNotEmpty()) {
        BanLiDownloadQualityDialog(
            prompt = downloadQualityPrompt(state.downloadConflicts),
            onDecision = state::resolveDownloadConflict,
        )
    }
}
