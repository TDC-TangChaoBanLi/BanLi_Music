/**
 * 文件作用：提供列表 A-Z 索引与拖动字母气泡组件。
 */
package com.banlimusic.app.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banlimusic.app.domain.sort.AlphabetSection

private val INDEX_LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }

/**
 * Vertical A-Z rail. Tapping jumps immediately; long-pressing and dragging
 * scrolls continuously as the finger moves. The rail only jumps to sections
 * that exist in the current sorted/filtered list.
 */
@Composable
fun AlphabetIndex(
    sections: List<AlphabetSection>,
    currentSection: String?,
    onSelect: (AlphabetSection) -> Unit,
    modifier: Modifier = Modifier,
    onDragLetter: (String?) -> Unit = {},
) {
    if (sections.isEmpty()) return
    val sectionByLabel = remember(sections) { sections.associateBy { it.label } }
    var heightPx by remember { mutableIntStateOf(1) }

    fun selectAt(y: Float) {
        val index = ((y / heightPx) * INDEX_LETTERS.size)
            .toInt()
            .coerceIn(0, INDEX_LETTERS.lastIndex)
        val label = INDEX_LETTERS[index]
        sectionByLabel[label]?.let(onSelect)
        onDragLetter(label)
    }

    Box(modifier = modifier.width(26.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(sections) {
                    detectTapGestures { offset -> selectAt(offset.y) }
                }
                .pointerInput(sections) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            selectAt(offset.y)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            selectAt(change.position.y)
                        },
                        onDragEnd = { onDragLetter(null) },
                        onDragCancel = { onDragLetter(null) },
                    )
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            INDEX_LETTERS.forEach { letter ->
                val active = letter == currentSection
                Text(
                    text = letter,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun AlphabetDragBubble(letter: String?, modifier: Modifier = Modifier) {
    if (letter == null) return
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
    ) {
        Text(
            text = letter,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
