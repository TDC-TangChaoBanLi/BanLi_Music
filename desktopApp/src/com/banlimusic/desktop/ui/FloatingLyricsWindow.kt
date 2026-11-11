/**
 * 文件作用：桌面端悬浮歌词窗口，支持拖动、锁定、关闭、字号与颜色设置。
 */
package com.banlimusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.rememberWindowState
import com.banlimusic.app.core.common.LrcLine
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.domain.playback.LrcParser
import com.banlimusic.desktop.DesktopContainer
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.CancellationException

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
    var locked by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(width = 620.dp, height = 156.dp)

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

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = "BanLiMusic 桌面歌词",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(window) {
            withFrameNanos { }
            val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            val size = window.size
            window.setLocation(
                bounds.x + (bounds.width - size.width).coerceAtLeast(0) / 2,
                bounds.y + bounds.height - size.height - 72,
            )
        }
        BanLiDesktopTheme(settings.themeMode, settings.customThemeColor) {
            FloatingLyricsContent(
                settings = settings,
                lines = lyricsLines,
                loading = lyricsLoading,
                positionMs = playback.positionMs,
                locked = locked,
                onToggleLock = { locked = !locked },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun WindowScope.FloatingLyricsContent(
    settings: AppSettings,
    lines: List<LrcLine>,
    loading: Boolean,
    positionMs: Long,
    locked: Boolean,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC101418)),
    ) {
        if (!locked) {
            WindowDraggableArea(modifier = Modifier.fillMaxSize()) {}
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
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
                style = TextStyle(
                    shadow = Shadow(Color.Black, blurRadius = 10f),
                ),
            )
            if (nextLine != null) {
                Text(
                    text = nextLine,
                    color = unplayedColor,
                    fontSize = (settings.floatingLyricsFontSize - 2).coerceAtLeast(12).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                    style = TextStyle(
                        shadow = Shadow(Color.Black, blurRadius = 8f),
                    ),
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (locked) "解锁歌词" else "锁定歌词",
                    tint = Color.White.copy(alpha = 0.88f),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭桌面歌词",
                    tint = Color.White.copy(alpha = 0.88f),
                )
            }
        }
        if (locked) {
            Text(
                text = "已锁定",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 4.dp),
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
