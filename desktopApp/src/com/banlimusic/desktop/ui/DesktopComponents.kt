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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.model.Track
import com.banlimusic.shared.ui.components.BanLiBadge
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiErrorCard
import com.banlimusic.shared.ui.components.BanLiSettingsCard
import com.banlimusic.shared.ui.components.BanLiTrackRow
import com.banlimusic.shared.ui.components.formatPlaybackTime as sharedFormatPlaybackTime
import java.util.Locale

/** Icons missing from the small material-icons-core artifact. */
object DesktopIcons {
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "Download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                close()
                moveTo(5f, 18f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                close()
            }
        }.build()
    }
}

fun formatPlaybackTime(millis: Long): String = sharedFormatPlaybackTime(millis)

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
    "local" -> "本地"
    "download" -> "已下载"
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
    showFavorite: Boolean = true,
    downloaded: Boolean = false,
    categoryLabel: String? = null,
    selected: Boolean? = null,
    onInfo: (() -> Unit)? = null,
) {
    val background = if (current) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val subtitle = buildString {
        append(track.artistLine)
        track.album?.title?.takeIf { it.isNotBlank() }?.let {
            append(" - ")
            append(it)
        }
    }
    BanLiTrackRow(
        title = track.title,
        subtitle = subtitle,
        artwork = { modifier ->
            DesktopTrackArtwork(
                track = track,
                loader = loader,
                allowRemote = allowRemoteArtwork,
                modifier = modifier,
            )
        },
        onClick = onPlay,
        selected = selected,
        containerColor = background,
        shape = RoundedCornerShape(12.dp),
        badges = {
            track.sources.firstOrNull()?.let { source ->
                BanLiBadge(
                    text = desktopProviderLabel(source.providerId),
                    modifier = Modifier.padding(end = 5.dp),
                )
            }
            if (downloaded) {
                BanLiBadge(
                    text = "已下载",
                    modifier = Modifier.padding(end = 5.dp),
                    highlighted = true,
                )
            }
            categoryLabel?.takeIf { it.isNotBlank() }?.let {
                BanLiBadge(
                    text = it,
                    modifier = Modifier.padding(end = 5.dp),
                    highlighted = true,
                )
            }
            if (current) {
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailing = {
            Spacer(Modifier.width(10.dp))
            Text(
                text = TextNormalizer.formatDurationMs(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onInfo != null) {
                IconButton(onClick = onInfo, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "查看文件信息",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = DesktopIcons.Download,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showFavorite) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏",
                        tint = if (favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onPlay) {
                Text("播放")
            }
        },
    )
}

@Composable
private fun RowBadge(text: String, highlighted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DesktopEmptyState(
    title: String,
    description: String,
    glyph: String = "♪",
) {
    BanLiEmptyState(
        title = title,
        description = description,
    )
}

@Composable
fun DesktopErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    BanLiErrorCard(message = message, onRetry = onRetry)
}

@Composable
fun SettingsCard(
    title: String,
    glyph: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    BanLiSettingsCard(
        title = title,
        leading = {
            Text(
                text = glyph,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        content = content,
    )
}