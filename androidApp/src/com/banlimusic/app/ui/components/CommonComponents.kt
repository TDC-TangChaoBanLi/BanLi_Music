package com.banlimusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.banlimusic.app.core.common.TextNormalizer
import com.banlimusic.app.core.model.Track

fun providerLabel(providerId: String): String = when (providerId) {
    "gd-netease" -> "网易云"
    "gd-joox" -> "JOOX"
    "gd-bilibili" -> "B站"
    "local" -> "本地"
    else -> providerId.substringAfterLast('-')
}

fun qualityLabel(kbps: Int): String = when (kbps) {
    128 -> "标准 128K"
    192 -> "较高 192K"
    320 -> "高品 320K"
    740 -> "无损 740K"
    999 -> "超无损 999K"
    else -> "${kbps}K"
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
    return if (index == 0) "${value.toLong()} ${units[index]}"
    else String.format(java.util.Locale.ROOT, "%.1f %s", value, units[index])
}

fun formatPlaybackTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val total = millis / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, total / 60, total % 60)
}

@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    artworkUrl: String? = track.artworkUrl,
) {
    val palettes = listOf(
        listOf(Color(0xFF0F766E), Color(0xFF134E4A)),
        listOf(Color(0xFF9A5B2C), Color(0xFF5C3317)),
        listOf(Color(0xFF3E5C8A), Color(0xFF1F2E4A)),
        listOf(Color(0xFF7A4A72), Color(0xFF3A2136)),
        listOf(Color(0xFF4C6B3A), Color(0xFF26361D)),
    )
    val palette = palettes[(track.title.hashCode() and Int.MAX_VALUE) % palettes.size]
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.fillMaxSize(0.42f),
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SourceBadge(providerId: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = providerLabel(providerId),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(
            track = track,
            modifier = Modifier.size(52.dp),
            cornerRadius = 10.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                track.primarySource?.let { SourceBadge(it.providerId) }
                if (track.sources.size > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+${track.sources.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = listOfNotNull(track.artistLine, track.album?.title).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.MusicNote,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            modifier = Modifier.size(46.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "搜索歌曲、歌手或专辑",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

fun trackSubtitle(track: Track): String =
    listOfNotNull(track.artistLine, track.album?.title, track.durationMs?.let(TextNormalizer::formatDurationMs))
        .joinToString(" · ")
