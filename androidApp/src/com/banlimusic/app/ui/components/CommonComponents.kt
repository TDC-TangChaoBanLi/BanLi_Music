/**
 * 文件作用：提供 Android 端的封面加载与列表封面策略。
 */
package com.banlimusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.banlimusic.app.MusicApplication
import com.banlimusic.app.core.model.Track

/** Allows track-list pages to fetch artwork from providers. Provided by the app root. */
val LocalRequestListArtwork = staticCompositionLocalOf { false }

private fun String.isLocalArtworkUri(): Boolean =
    startsWith("file://") || startsWith("content://") || startsWith("android.resource://")

/**
 * Android artwork loader. The visual card layout is local because Coil owns the
 * image pipeline; every list row itself comes from the shared UI.
 */
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
        mutableStateOf(artworkUrl?.takeIf { it.isLocalArtworkUri() })
    }
    LaunchedEffect(track.id, artworkUrl, allowRemote) {
        val container = (context.applicationContext as? MusicApplication)?.container
            ?: return@LaunchedEffect
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
