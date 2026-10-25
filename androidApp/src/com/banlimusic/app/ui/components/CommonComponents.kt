/**
 * 文件作用：提供封面、曲目行、来源徽标和音质文案等通用 Compose 组件。
 */
package com.banlimusic.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.model.QualityTiers
import com.banlimusic.app.core.model.Track

fun providerLabel(providerId: String): String = when (providerId) {
    "gd-netease" -> "网易云"
    "gd-joox" -> "JOOX"
    "gd-bilibili" -> "B站"
    "local" -> "本地"
    "download" -> "已下载"
    else -> providerId.substringAfterLast('-')
}

fun qualityLabel(kbps: Int): String = when (QualityTiers.normalize(kbps)) {
    128 -> "标准 128K"
    192 -> "较高 192K"
    320 -> "高品 320K"
    740 -> "无损 740K"
    else -> "${kbps}K"
}

fun qualityShortLabel(kbps: Int): String = when (QualityTiers.normalize(kbps)) {
    128 -> "标准"
    192 -> "较高"
    320 -> "高品"
    740 -> "无损"
    else -> "${kbps}K"
}

/** Display-only tier label: never exposes technical bitrates such as 128K. */
fun qualityTierLabel(kbps: Int): String = qualityShortLabel(kbps)

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

/** Allows track-list pages to fetch artwork from providers. Provided by the app root. */
val LocalRequestListArtwork = staticCompositionLocalOf { false }

private fun String.isLocalArtworkUri(): Boolean =
    startsWith("file://") || startsWith("content://") || startsWith("android.resource://")

@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    artworkUrl: String? = track.artworkUrl ?: track.album?.artworkUrl,
    /** The player page always resolves artwork regardless of the list setting. */
    requestRemoteArtwork: Boolean = false,
) {
    val context = LocalContext.current
    val allowRemote = requestRemoteArtwork || LocalRequestListArtwork.current
    var artworkModel by remember(track.id, artworkUrl, allowRemote) {
        // Local files can be shown immediately; remote URLs only when allowed.
        mutableStateOf(artworkUrl?.takeIf { it.isLocalArtworkUri() })
    }
    LaunchedEffect(track.id, artworkUrl, allowRemote) {
        val container = (context.applicationContext as? MusicApplication)?.container
            ?: return@LaunchedEffect
        // Cached artwork wins even when list requests are disabled. This covers
        // artwork produced by the player or by a completed download.
        val cached = try {
            container.mediaMetadataCache.artwork(track.id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (!cached.isNullOrBlank()) {
            artworkModel = cached
            return@LaunchedEffect
        }
        val local = artworkUrl?.takeIf { it.isLocalArtworkUri() }
        if (local != null) {
            artworkModel = local
            return@LaunchedEffect
        }
        if (!allowRemote) {
            artworkModel = null
            return@LaunchedEffect
        }
        if (!artworkUrl.isNullOrBlank()) {
            artworkModel = artworkUrl
            return@LaunchedEffect
        }
        // GD search results only carry an artwork key, not a URL. Resolve it lazily
        // through the repository (which is rate-limited and caches the file).
        val resolved = try {
            container.musicRepository.artwork(track)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (!resolved.isNullOrBlank()) artworkModel = resolved
    }
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
        if (!artworkModel.isNullOrBlank()) {
            AsyncImage(
                model = artworkModel,
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
            text = sourceDisplayNameAnnotated(providerId),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/** API prefix shown before the provider name, e.g. "GD" for GD Music Station. */
fun sourcePrefix(providerId: String): String? = when {
    providerId.startsWith("gd-") -> "GD"
    else -> null
}

/** Full display name: API prefix + short provider name, e.g. "GD网易云". */
fun sourceDisplayName(providerId: String): String {
    val prefix = sourcePrefix(providerId) ?: return providerLabel(providerId)
    val label = providerLabel(providerId)
    val latin = label.firstOrNull()?.code?.let { it < 128 } == true
    return if (latin) "$prefix $label" else "$prefix$label"
}

/** Same as [sourceDisplayName] but with the API prefix rendered in bold. */
fun sourceDisplayNameAnnotated(providerId: String): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        val prefix = sourcePrefix(providerId)
        val label = providerLabel(providerId)
        if (prefix != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(prefix) }
            if (label.firstOrNull()?.code?.let { it < 128 } == true) append(" ")
        }
        append(label)
    }

private val NON_PROVIDER_SOURCE_IDS = setOf("local", "download")

/**
 * Renders every meaningful source for a track: the API provider, a "本地" badge for
 * imported files and an extra "已下载" badge for downloaded copies.
 */
@Composable
fun TrackSourceBadges(track: Track, downloaded: Boolean = false) {
    val providerSources = track.sources.filterNot { it.providerId in NON_PROVIDER_SOURCE_IDS }
    val hasLocalImport = track.sources.any { it.providerId == "local" }
    val hasDownload = downloaded || track.sources.any { it.providerId == "download" }
    if (hasLocalImport) {
        SourceBadge("local")
    } else {
        providerSources.firstOrNull()?.let { SourceBadge(it.providerId) }
        if (providerSources.size > 1) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "+${providerSources.size - 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (hasDownload) {
        Spacer(Modifier.width(4.dp))
        SourceBadge("download")
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    /** Renders a read-only heart when the row has no favourite toggle. */
    showFavoriteState: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    /** Extra badge shown after the source badge, e.g. the containing favourite folder. */
    categoryLabel: String? = null,
    /** Marks tracks that already have a completed download, even if the saved
     * favourite JSON has no download source. */
    downloaded: Boolean = false,
    /** Optional info action rendered as a trailing icon (e.g. local file location). */
    infoAction: (() -> Unit)? = null,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    selectionDragModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(selectionDragModifier)
            .then(clickModifier)
            .padding(start = 8.dp, top = 7.dp, end = 2.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        TrackArtwork(
            track = track,
            modifier = Modifier.size(52.dp),
            cornerRadius = 10.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 4.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val titleMaxWidth = maxWidth * 0.66f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = track.title,
                        modifier = Modifier
                            .widthIn(max = titleMaxWidth)
                            .basicMarquee(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(Modifier.weight(1f))
                    val subtitle = listOfNotNull(
                        track.album?.title?.takeIf { it.isNotBlank() },
                        track.artistLine,
                    ).joinToString("·")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            modifier = Modifier.basicMarquee(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackSourceBadges(track, downloaded = downloaded)
                if (!categoryLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = categoryLabel,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            if (infoAction != null) {
                IconButton(onClick = infoAction, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "查看信息",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else if (showFavoriteState && isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "已收藏",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(16.dp),
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
