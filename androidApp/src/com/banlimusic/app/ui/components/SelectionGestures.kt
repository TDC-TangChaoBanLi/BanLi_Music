/**
 * 文件作用：实现列表长按拖拽多选的手势支持。
 */
package com.banlimusic.app.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drag-to-select support for a LazyColumn.
 *
 * Attach this to the list itself rather than to every row: the row that started
 * the gesture is disposed once auto-scroll moves it off screen, which used to end
 * the drag silently and select items below the finger. Positions here are viewport
 * coordinates, so they remain correct while the list scrolls.
 */
fun Modifier.dragSelect(
    enabled: Boolean,
    listState: LazyListState,
    validKeys: Set<String>,
    onSelect: (String) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(listState, validKeys) {
        coroutineScope {
            val edgeThreshold = 72.dp.toPx()
            val scrollStep = 14.dp.toPx()
            val reversalSlop = viewConfiguration.touchSlop
            var lastKey: String? = null
            var pointerY = 0f
            var lastPointerY = 0f
            var lastDirection = 0
            var autoScroll: Job? = null

            fun keyAt(y: Float): String? {
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                    y.toInt() in info.offset until (info.offset + info.size)
                } ?: return null
                return (item.key as? String)?.takeIf { it in validKeys }
            }

            fun handle(y: Float) {
                val key = keyAt(y) ?: return
                if (key != lastKey) {
                    lastKey = key
                    onSelect(key)
                }
            }

            fun stopAutoScroll() {
                autoScroll?.cancel()
                autoScroll = null
            }

            fun startAutoScroll() {
                if (autoScroll != null) return
                autoScroll = launch {
                    while (isActive) {
                        val info = listState.layoutInfo
                        val top = info.viewportStartOffset + edgeThreshold
                        val bottom = info.viewportEndOffset - edgeThreshold
                        val delta = when {
                            pointerY < top -> -scrollStep
                            pointerY > bottom -> scrollStep
                            else -> 0f
                        }
                        if (delta == 0f) break
                        // UserInput priority preempts the pointer-driven scroll
                        // session, otherwise scrollBy waits on the scroll mutex.
                        listState.scroll(MutatePriority.UserInput) { scrollBy(delta) }
                        handle(pointerY)
                        delay(16)
                    }
                    autoScroll = null
                }
            }

            fun nearEdge(): Boolean {
                val info = listState.layoutInfo
                return pointerY < info.viewportStartOffset + edgeThreshold ||
                    pointerY > info.viewportEndOffset - edgeThreshold
            }

            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                var aborted = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            aborted = true
                            return@withTimeoutOrNull
                        }
                        if ((change.position - down.position).getDistance() >
                            viewConfiguration.touchSlop
                        ) {
                            aborted = true
                            return@withTimeoutOrNull
                        }
                    }
                }
                // Timeout without movement means the long press fired; moving or
                // releasing first must keep the list's normal scrolling behaviour.
                if (aborted) return@awaitEachGesture

                lastKey = null
                pointerY = down.position.y
                lastPointerY = pointerY
                lastDirection = 0
                handle(pointerY)
                if (nearEdge()) startAutoScroll()
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Consuming in the Initial pass keeps the list's own
                        // scroll detector from stealing the drag.
                        change.consume()
                        val newY = change.position.y
                        val deltaY = newY - lastPointerY
                        if (abs(deltaY) > reversalSlop) {
                            val direction = if (deltaY > 0) 1 else -1
                            if (lastDirection != 0 &&
                                direction != lastDirection &&
                                lastKey != null
                            ) {
                                // Reversing direction must also undo the item at
                                // the turning point, not only the previous rows.
                                onSelect(lastKey!!)
                            }
                            lastDirection = direction
                            lastPointerY = newY
                        }
                        pointerY = newY
                        handle(pointerY)
                        if (nearEdge()) startAutoScroll() else stopAutoScroll()
                        if (!change.pressed) break
                    }
                } finally {
                    stopAutoScroll()
                    lastKey = null
                }
            }
        }
    }
}
