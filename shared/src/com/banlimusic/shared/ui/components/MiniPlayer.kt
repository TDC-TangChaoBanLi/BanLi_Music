/**
 * 文件作用：两端共用的迷你播放条，包含播放控件、收藏与常驻占位状态。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Track

data class MiniPlayerUiState(
    val track: Track? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Shared mini player. Android hides it when nothing is playing; desktop shows a
 * placeholder so the bar is always present on the main pages.
 */
@Composable
fun BanLiMiniPlayer(
    state: MiniPlayerUiState,
    artwork: @Composable (Modifier) -> Unit,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    showPlaceholder: Boolean = false,
    placeholderTitle: String = "还没有播放歌曲",
    placeholderSubtitle: String = "从首页、搜索或音乐库选择一首歌开始播放。",
    extraInfo: (@Composable () -> Unit)? = null,
    volume: Float? = null,
    onVolumeChange: ((Float) -> Unit)? = null,
    onSeek: ((Long) -> Unit)? = null,
) {
    val track = state.track
    if (track == null) {
        if (!showPlaceholder) return
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 10.dp,
                bottomEnd = 10.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = placeholderTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = placeholderSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
                }
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "播放")
                }
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                }
            }
        }
        return
    }

    BanLiPlaybackBar(
        title = state.title ?: track.title,
        subtitle = state.subtitle ?: track.artistLine,
        progress = state.progress,
        artwork = artwork,
        onClick = onClick,
        controls = {
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
            }
            IconButton(
                onClick = onPrevious,
                enabled = state.hasPrevious,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = onTogglePlayPause,
                enabled = !state.isBuffering,
                modifier = Modifier.size(42.dp),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                    )
                }
            }
            IconButton(
                onClick = onNext,
                enabled = state.hasNext,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(22.dp),
                )
            }
            if (volume != null && onVolumeChange != null) {
                VolumeControl(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier.size(38.dp),
                )
            }
        },
        modifier = modifier,
        extraInfo = extraInfo?.let { { it() } },
        onSeek = if (onSeek != null && state.durationMs > 0L) {
            { fraction -> onSeek((fraction * state.durationMs).toLong()) }
        } else {
            null
        },
    )
}
