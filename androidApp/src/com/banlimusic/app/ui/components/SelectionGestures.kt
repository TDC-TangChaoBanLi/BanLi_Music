package com.banlimusic.app.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Drag-to-select support for LazyColumn rows. The caller must use the track id as
 * the item key so the drag position can be mapped back to a stable identifier.
 */
fun Modifier.dragSelect(
    enabled: Boolean,
    listState: LazyListState,
    validKeys: Set<String>,
    onSelect: (String) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, listState, validKeys) {
        var lastKey: String? = null

        fun handle(y: Float) {
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                y.toInt() in info.offset..(info.offset + info.size)
            } ?: return
            val key = item.key as? String ?: return
            if (key in validKeys && key != lastKey) {
                lastKey = key
                onSelect(key)
            }
        }

        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> handle(offset.y) },
            onDrag = { change, _ ->
                change.consume()
                handle(change.position.y)
            },
            onDragEnd = { lastKey = null },
            onDragCancel = { lastKey = null },
        )
    }
}
