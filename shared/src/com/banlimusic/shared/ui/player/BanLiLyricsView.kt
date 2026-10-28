/**
 * 文件作用：两端共用的歌词视图，包含自动滚动与长按拖动定位。
 */
package com.banlimusic.shared.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banlimusic.app.core.model.Lyrics
import com.banlimusic.app.domain.playback.LrcParser
import com.banlimusic.app.domain.playback.stripTimestamps
import com.banlimusic.shared.ui.components.BanLiEmptyState
import com.banlimusic.shared.ui.components.formatPlaybackTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun BanLiLyricsView(
    lyrics: Lyrics?,
    loading: Boolean,
    error: String?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    headerTitle: String? = null,
    headerSubtitle: String? = null,
    emptyTitle: String = "暂无歌词",
    emptyDescription: String = "当前音乐源没有返回这首歌的歌词。",
) {
    val lines = remember(lyrics?.raw) { lyrics?.raw?.let(LrcParser::parse).orEmpty() }
    val listState = rememberLazyListState()
    val duration = durationMs.coerceAtLeast(0L)
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var boxTopInWindow by remember { mutableFloatStateOf(0f) }
    var listTopInWindow by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .onGloballyPositioned { boxTopInWindow = it.positionInWindow().y }
            .pointerInput(lines) {
                fun updateTarget(windowY: Float) {
                    val listY = windowY - listTopInWindow
                    val visible = listState.layoutInfo.visibleItemsInfo
                    if (visible.isEmpty()) return
                    val item = visible.firstOrNull {
                        listY.toInt() in it.offset until (it.offset + it.size)
                    } ?: visible.minByOrNull {
                        abs(it.offset + it.size / 2 - listY)
                    } ?: return
                    val index = item.index
                    if (index in lines.indices) {
                        scrubIndex = index
                        scrubMs = lines[index].timeMs
                    }
                }
                coroutineScope {
                    val edgeThreshold = 72.dp.toPx()
                    val scrollStep = 12.dp.toPx()
                    var pointerWindowY = 0f
                    var autoScroll: Job? = null

                    fun stopAutoScroll() {
                        autoScroll?.cancel()
                        autoScroll = null
                    }

                    fun startAutoScroll() {
                        if (autoScroll != null) return
                        autoScroll = launch {
                            while (isActive) {
                                val info = listState.layoutInfo
                                val viewportTop = listTopInWindow + info.viewportStartOffset
                                val viewportBottom = listTopInWindow + info.viewportEndOffset
                                val delta = when {
                                    pointerWindowY < viewportTop + edgeThreshold -> -scrollStep
                                    pointerWindowY > viewportBottom - edgeThreshold -> scrollStep
                                    else -> 0f
                                }
                                if (delta == 0f) {
                                    stopAutoScroll()
                                    break
                                }
                                listState.scrollBy(delta)
                                updateTarget(pointerWindowY)
                                delay(16)
                            }
                        }
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            pointerWindowY = boxTopInWindow + offset.y
                            updateTarget(pointerWindowY)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pointerWindowY = boxTopInWindow + change.position.y
                            updateTarget(pointerWindowY)
                            val info = listState.layoutInfo
                            val viewportTop = listTopInWindow + info.viewportStartOffset
                            val viewportBottom = listTopInWindow + info.viewportEndOffset
                            val nearEdge = pointerWindowY < viewportTop + edgeThreshold ||
                                pointerWindowY > viewportBottom - edgeThreshold
                            if (nearEdge) startAutoScroll() else stopAutoScroll()
                        },
                        onDragEnd = {
                            stopAutoScroll()
                            scrubMs?.let(onSeek)
                            scrubIndex = null
                            scrubMs = null
                        },
                        onDragCancel = {
                            stopAutoScroll()
                            scrubIndex = null
                            scrubMs = null
                        },
                    )
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerTitle ?: "未知歌曲",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!headerSubtitle.isNullOrBlank()) {
                            Text(
                                text = headerSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }

                error != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onReload) { Text("重试") }
                    }
                }

                lyrics == null -> {
                    BanLiEmptyState(
                        title = emptyTitle,
                        description = emptyDescription,
                    )
                }

                lines.isEmpty() -> {
                    Text(
                        text = lyrics.raw.stripTimestamps(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                else -> {
                    val currentIndex = lines
                        .indexOfLast { it.timeMs <= positionMs }
                        .coerceAtLeast(0)
                    val activeIndex = scrubIndex ?: currentIndex
                    LaunchedEffect(currentIndex, scrubIndex) {
                        if (scrubIndex == null && currentIndex in lines.indices) {
                            // Keep the active line on the third visible row.
                            val targetIndex = (currentIndex - 2).coerceAtLeast(0)
                            runCatching { listState.animateScrollToItem(targetIndex) }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = scrubIndex == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onGloballyPositioned { listTopInWindow = it.positionInWindow().y },
                    ) {
                        itemsIndexed(lines) { index, line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (index == scrubIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(2.dp),
                                            ),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = line.text,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = if (index == activeIndex) 17.sp else 15.sp,
                                    fontWeight = if (index == activeIndex) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = if (index == activeIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        scrubMs?.let { target ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "${formatPlaybackTime(target)} / ${formatPlaybackTime(duration)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
