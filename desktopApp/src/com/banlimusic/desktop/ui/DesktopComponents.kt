package com.banlimusic.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.model.Track
import java.util.Locale

fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        String.format(Locale.ROOT, "%.1f %s", value, units[index])
    }
}

fun desktopProviderLabel(providerId: String): String = when (providerId) {
    "gd-netease" -> "GD网易云"
    "gd-joox" -> "GD JOOX"
    "gd-bilibili" -> "GD B站"
    else -> providerId
}

@Composable
fun DesktopTrackArtwork(
    track: Track,
    loader: DesktopArtworkLoader,
    allowRemote: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    var bitmap by remember(track.id) { mutableStateOf(loader.cached(track.id)) }
    LaunchedEffect(track.id, allowRemote) {
        if (bitmap == null) {
            bitmap = loader.load(track, allowRemote)
        }
    }
    val palettes = listOf(
        listOf(Color(0xFF0F766E), Color(0xFF134E4A)),
        listOf(Color(0xFF9A5B2C), Color(0xFF5C3317)),
        listOf(Color(0xFF3E5C8A), Color(0xFF1F2E4A)),
        listOf(Color(0xFF7A4A72), Color(0xFF3A2136)),
        listOf(Color(0xFF4C6B3A), Color(0xFF26361D)),
    )
    val palette = palettes[(track.id.hashCode() and Int.MAX_VALUE) % palettes.size]
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap as ImageBitmap,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "♪",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 24.sp,
            )
        }
    }
}

@Composable
fun DesktopTrackRow(
    track: Track,
    loader: DesktopArtworkLoader,
    allowRemoteArtwork: Boolean,
    favorite: Boolean,
    current: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    val background = if (current) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(12.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopTrackArtwork(
                track = track,
                loader = loader,
                allowRemote = allowRemoteArtwork,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(track.artistLine)
                        track.album?.title?.takeIf { it.isNotBlank() }?.let {
                            append(" - ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current) {
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            track.sources.firstOrNull()?.let { source ->
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = desktopProviderLabel(source.providerId),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = TextNormalizer.formatDurationMs(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    tint = if (favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onPlay) {
                Text("播放")
            }
        }
    }
}

@Composable
fun DesktopEmptyState(
    title: String,
    description: String,
    glyph: String = "♪",
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = glyph,
            fontSize = 36.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DesktopErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    glyph: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(glyph, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
