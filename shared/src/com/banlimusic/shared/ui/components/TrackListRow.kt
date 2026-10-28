/**
 * 文件作用：两端共用的曲目行，统一来源徽标、收藏夹、心形按钮与信息按钮布局。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track

/**
 * One track row used by every list on both platforms. Callers only supply the
 * artwork (Coil on Android, AWT bitmaps on desktop) and platform actions.
 */
@Composable
fun BanLiTrackListRow(
    track: Track,
    artwork: @Composable (Modifier) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = listOfNotNull(
        track.album?.title?.takeIf { it.isNotBlank() },
        track.artistLine,
    ).joinToString("·"),
    isFavorite: Boolean = false,
    showFavoriteState: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    downloaded: Boolean = false,
    categoryLabel: String? = null,
    preferredProviderId: String? = null,
    onSourceClick: (() -> Unit)? = null,
    infoAction: (() -> Unit)? = null,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    gestureModifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(14.dp),
    extraBadges: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    BanLiTrackRow(
        title = track.title,
        subtitle = subtitle,
        artwork = artwork,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        onLongClick = onLongClick,
        gestureModifier = gestureModifier,
        containerColor = containerColor,
        shape = shape,
        badges = {
            TrackSourceBadges(
                track = track,
                downloaded = downloaded,
                preferredProviderId = preferredProviderId,
                onSourceClick = onSourceClick,
            )
            extraBadges?.invoke(this)
            if (!categoryLabel.isNullOrBlank()) {
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = categoryLabel,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailing = {
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
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "已收藏",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                trailing?.invoke(this)
            }
        },
    )
}

/** Two-line trailing block used by downloaded and cached track lists. */
@Composable
fun TrackQualitySizeLabel(
    qualityKbps: Int,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = if (qualityKbps > 0) qualityTierLabel(qualityKbps) else "音质未知",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = formatMegabytes(sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
