/**
 * 文件作用：桌面端悬浮歌词窗口，支持拖动、锁定、关闭、字号与颜色设置。
 */
package com.banlimusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.banlimusic.app.core.common.LrcLine
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.domain.playback.LrcParser
import com.banlimusic.desktop.DesktopContainer
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A separate always-on-top window that follows the current player position. */
@Composable
fun DesktopFloatingLyrics(
    container: DesktopContainer,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val playback by container.player.state.collectAsState()
    val track = playback.track
    // Keep the desktop free of an idle placeholder window.
    if (track == null) return
    var lyricsLines by remember(track.id) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var lyricsLoading by remember(track.id) { mutableStateOf(true) }
    var locked by remember { mutableStateOf(settings.floatingLyricsLocked) }
    var hoveringText by remember { mutableStateOf(false) }
    var lyricsWindow by remember { mutableStateOf<ComposeDialog?>(null) }
    val scope = rememberCoroutineScope()
    val dialogState = rememberDialogState(width = 620.dp, height = 156.dp)
    val toggleLock: () -> Unit = {
        val next = !locked
        locked = next
        scope.launch {
            container.settingsRepository.setFloatingLyricsLocked(next)
        }
        Unit
    }

    LaunchedEffect(settings.floatingLyricsLocked) {
        locked = settings.floatingLyricsLocked
    }

    LaunchedEffect(track.id) {
        lyricsLoading = true
        try {
            val lyrics = container.musicRepository.lyrics(track)
            lyricsLines = lyrics?.raw?.let(LrcParser::parse).orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            lyricsLines = emptyList()
        } finally {
            lyricsLoading = false
        }
    }

    // Keep the declared position in sync with the dragged window so later
    // recompositions cannot snap the lyrics back to the screen centre.
    LaunchedEffect(locked, lyricsWindow) {
        val current = lyricsWindow
        if (locked && current != null) {
            dialogState.position = WindowPosition(current.x.dp, current.y.dp)
        }
    }

    DialogWindow(
        onCloseRequest = onClose,
        state = dialogState,
        title = "BanLiMusic 桌面歌词",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(window) {
            lyricsWindow = window
            withFrameNanos { }
            val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            val size = window.size
            window.setLocation(
                bounds.x + (bounds.width - size.width).coerceAtLeast(0) / 2,
                bounds.y + bounds.height - size.height - 72,
            )
            dialogState.position = WindowPosition(window.x.dp, window.y.dp)
        }
        DisposableEffect(window) {
            onDispose { lyricsWindow = null }
        }
        BanLiDesktopTheme(settings.themeMode, settings.customThemeColor) {
            FloatingLyricsContent(
                window = window,
                settings = settings,
                lines = lyricsLines,
                loading = lyricsLoading,
                positionMs = playback.positionMs,
                locked = locked,
                onHoveringChanged = { hoveringText = it },
                onToggleLock = toggleLock,
                onClose = onClose,
            )
        }
    }

    val currentLyricsWindow = lyricsWindow
    if (locked && hoveringText && currentLyricsWindow != null) {
        UnlockButtonWindow(
            mainWindow = currentLyricsWindow,
            settings = settings,
            onToggleLock = toggleLock,
        )
    }
}

@Composable
private fun DialogWindowScope.FloatingLyricsContent(
    window: ComposeDialog,
    settings: AppSettings,
    lines: List<LrcLine>,
    loading: Boolean,
    positionMs: Long,
    locked: Boolean,
    onHoveringChanged: (Boolean) -> Unit,
    onToggleLock: () -> Unit,
    onClose: () -> Unit,
) {
    val playedColor = Color(
        settings.floatingLyricsPlayedColor ?: Color.White.toArgb(),
    )
    val unplayedColor = Color(
        settings.floatingLyricsUnplayedColor ?: Color.White.copy(alpha = 0.62f).toArgb(),
    )
    val currentIndex = lines.indexOfLast { it.timeMs <= positionMs }
    val currentLine = lines.getOrNull(currentIndex)?.text
        ?: lines.firstOrNull()?.text
        ?: if (loading) "正在加载歌词…" else "暂无歌词"
    val nextLine = if (settings.floatingLyricsLines >= 2) {
        lines.getOrNull((currentIndex + 1).coerceAtLeast(0))?.text
            ?.takeIf { it != currentLine }
    } else {
        null
    }
    var hoveringText by remember { mutableStateOf(false) }
    val clickThrough = remember(window) { DesktopWindowClickThrough(window) }

    DisposableEffect(window) {
        onDispose {
            clickThrough.setClickThrough(false)
            clickThrough.close()
        }
    }

    // Locked windows stay fully click-through. The unlock button lives in a
    // separate window composed by the parent so this window never moves.
    LaunchedEffect(locked, window) {
        if (!locked) {
            clickThrough.setClickThrough(false)
            if (hoveringText) {
                hoveringText = false
                onHoveringChanged(false)
            }
            return@LaunchedEffect
        }
        while (isActive) {
            clickThrough.setClickThrough(true)
            val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
            val hot = pointer != null &&
                window.isVisible &&
                lyricHotspot(window.bounds).contains(pointer)
            if (hot != hoveringText) {
                hoveringText = hot
                onHoveringChanged(hot)
            }
            delay(80L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (locked) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xCC101418))
                },
            ),
    ) {
        if (!locked) {
            WindowDraggableArea(modifier = Modifier.fillMaxSize()) {}
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = currentLine,
                color = playedColor,
                fontSize = settings.floatingLyricsFontSize.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.88f),
                style = TextStyle(
                    shadow = Shadow(Color.Black, blurRadius = if (locked) 14f else 10f),
                ),
            )
            if (nextLine != null) {
                Text(
                    text = nextLine,
                    color = unplayedColor,
                    fontSize = (settings.floatingLyricsFontSize - 2).coerceAtLeast(12).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(top = 8.dp),
                    style = TextStyle(
                        shadow = Shadow(Color.Black, blurRadius = if (locked) 12f else 8f),
                    ),
                )
            }
        }
        if (!locked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SmallOverlayIconButton(
                    icon = Icons.Rounded.LockOpen,
                    contentDescription = "锁定歌词",
                    onClick = onToggleLock,
                )
                SmallOverlayIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭桌面歌词",
                    onClick = onClose,
                )
            }
        }
    }
}

/** Small always-on-top window with only the unlock button. */
@Composable
private fun UnlockButtonWindow(
    mainWindow: ComposeDialog,
    settings: AppSettings,
    onToggleLock: () -> Unit,
) {
    val unlockState = rememberDialogState(width = 28.dp, height = 28.dp)
    var visible by remember { mutableStateOf(false) }
    DialogWindow(
        onCloseRequest = {},
        state = unlockState,
        visible = visible,
        title = "BanLiMusic 歌词解锁",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(Unit) {
            val bounds = mainWindow.bounds
            val buttonSize = 28
            val margin = 8
            val x = bounds.x + bounds.width - buttonSize - margin
            val y = bounds.y + (bounds.height - buttonSize) / 2
            window.setLocation(x, y)
            // Keep the declared position stable so later recompositions do
            // not move the button back to the screen centre.
            unlockState.position = WindowPosition(x.dp, y.dp)
            visible = true
        }
        BanLiDesktopTheme(settings.themeMode, settings.customThemeColor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC101418))
                    .clickable(onClick = onToggleLock),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "解锁歌词",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Compact square button used by the floating lyrics controls. */
@Composable
private fun SmallOverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Vertical band around the centred lyric text. Fractions keep the hit test
 * independent from DPI scaling while still allowing the whole window to be
 * click-through outside the lyrics.
 */
private fun lyricHotspot(bounds: Rectangle): Rectangle {
    val insetX = (bounds.width * 0.01f).toInt()
    val top = bounds.y + (bounds.height * 0.18f).toInt()
    val bottom = bounds.y + (bounds.height * 0.84f).toInt()
    return Rectangle(
        bounds.x + insetX,
        top,
        (bounds.width - insetX * 2).coerceAtLeast(1),
        (bottom - top).coerceAtLeast(1),
    )
}

private fun Rectangle.contains(point: Point): Boolean =
    point.x in x until (x + width) && point.y in y until (y + height)
