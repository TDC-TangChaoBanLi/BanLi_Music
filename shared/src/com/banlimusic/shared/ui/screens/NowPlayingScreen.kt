/**
 * 文件作用：两端共用的正在播放页布局，支持平板双栏与手机分页，并保留平台操作插槽。
 */
package com.banlimusic.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.core.model.Track
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.BanLiProgressSection
import com.banlimusic.shared.ui.player.BanLiLyricsView

enum class NowPlayingLayout { TWO_PANE, PAGER }

/** Platform-independent snapshot of the playback state. */
data class NowPlayingUiState(
    val track: Track? = null,
    val title: String? = null,
    val artist: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

@Composable
fun BanLiNowPlayingScreen(
    state: NowPlayingUiState,
    lyrics: Lyrics?,
    lyricsLoading: Boolean,
    lyricsError: String?,
    layout: NowPlayingLayout,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onReloadLyrics: () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    controls: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    backIcon: ImageVector? = null,
    metadata: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    topBarActions: (@Composable RowScope.() -> Unit)? = null,
    rightPanel: (@Composable () -> Unit)? = null,
    initialShowLyrics: Boolean = false,
    onLyricsVisible: (Boolean) -> Unit = {},
) {
    val track = state.track
    val headerTitle = state.title ?: track?.title ?: "未知歌曲"
    val headerSubtitle = listOfNotNull(
        track?.album?.title?.takeIf { it.isNotBlank() },
        state.artist ?: track?.artistLine,
    ).joinToString("·")
    val pagerState = rememberPagerState(
        initialPage = if (initialShowLyrics) 1 else 0,
        pageCount = { 2 },
    )

    LaunchedEffect(pagerState.currentPage, layout) {
        onLyricsVisible(layout == NowPlayingLayout.TWO_PANE || pagerState.currentPage == 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            if (track == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    NowPlayingTopBar(onBack = onBack, backIcon = backIcon, actions = topBarActions)
                    BanLiEmptyState(
                        title = "还没有播放歌曲",
                        description = "从首页、搜索或音乐库选择一首歌开始播放。",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    NowPlayingTopBar(onBack = onBack, backIcon = backIcon, actions = topBarActions)
                    if (layout == NowPlayingLayout.TWO_PANE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            NowPlayingDetailsPane(
                                state = state,
                                artwork = artwork,
                                controls = controls,
                                onSeek = onSeek,
                                metadata = metadata,
                                actions = actions,
                                compactArtwork = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            if (rightPanel != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    rightPanel()
                                }
                            } else {
                                BanLiLyricsView(
                                    lyrics = lyrics,
                                    loading = lyricsLoading,
                                    error = lyricsError,
                                    positionMs = state.positionMs,
                                    durationMs = state.durationMs,
                                    onSeek = onSeek,
                                    onReload = onReloadLyrics,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    headerTitle = headerTitle,
                                    headerSubtitle = headerSubtitle,
                                )
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            if (page == 0) {
                                NowPlayingDetailsPane(
                                    state = state,
                                    artwork = artwork,
                                    controls = controls,
                                    onSeek = onSeek,
                                    metadata = metadata,
                                    actions = actions,
                                    compactArtwork = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                BanLiLyricsView(
                                    lyrics = lyrics,
                                    loading = lyricsLoading,
                                    error = lyricsError,
                                    positionMs = state.positionMs,
                                    durationMs = state.durationMs,
                                    onSeek = onSeek,
                                    onReload = onReloadLyrics,
                                    modifier = Modifier.fillMaxSize(),
                                    headerTitle = headerTitle,
                                    headerSubtitle = headerSubtitle,
                                )
                            }
                        }
                        NowPlayingPageIndicator(
                            pageCount = 2,
                            currentPage = pagerState.currentPage,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    onBack: () -> Unit,
    backIcon: ImageVector?,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backIcon != null) {
            IconButton(onClick = onBack) {
                Icon(backIcon, contentDescription = "返回")
            }
        } else {
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(
            text = "正在播放",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actions != null) {
            actions()
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun NowPlayingDetailsPane(
    state: NowPlayingUiState,
    artwork: @Composable (Modifier) -> Unit,
    controls: @Composable (Modifier) -> Unit,
    onSeek: (Long) -> Unit,
    metadata: (@Composable ColumnScope.() -> Unit)?,
    actions: (@Composable ColumnScope.() -> Unit)?,
    compactArtwork: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    BoxWithConstraints(modifier = modifier) {
        val artworkSize = if (compactArtwork) {
            minOf(maxWidth * 0.66f, maxHeight * 0.46f)
        } else {
            minOf(maxWidth * 0.78f, maxHeight * 0.5f)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            artwork(Modifier.size(artworkSize))
            Spacer(Modifier.height(18.dp))
            Text(
                text = state.title ?: track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.artist ?: track.artistLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadata != null) {
                Spacer(Modifier.height(10.dp))
                metadata()
            }
            Spacer(Modifier.height(14.dp))
            BanLiProgressSection(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
                enabled = state.durationMs > 0L,
            )
            Spacer(Modifier.height(8.dp))
            controls(Modifier.fillMaxWidth())
            if (actions != null) {
                Spacer(Modifier.height(10.dp))
                actions()
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NowPlayingPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}
