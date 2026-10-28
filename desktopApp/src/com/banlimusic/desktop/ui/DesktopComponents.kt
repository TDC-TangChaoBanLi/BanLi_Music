package com.banlimusic.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banlimusic.app.core.model.Track

/**
 * Desktop artwork loader. Rows and screens are shared; only the image pipeline
 * differs because desktop uses AWT/Skiko bitmaps instead of Coil.
 */
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
