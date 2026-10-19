package com.phuuun.legato.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Drag-to-reorder for fixed-height rows: the held row follows the finger and lands where it is released.
 * ponytail: rows don't part live and the list doesn't auto-scroll; reach for a reorder library if that feels stiff.
 */
class Reorder internal constructor(private val rowPx: Float, private val onMove: (from: Int, to: Int) -> Unit) {
    var dragging by mutableStateOf<Int?>(null)
        private set
    private var offset by mutableFloatStateOf(0f)

    /** On the whole row: lifts it above its neighbours and moves it with the finger. */
    fun row(pos: Int): Modifier = Modifier.zIndex(if (dragging == pos) 1f else 0f).graphicsLayer { translationY = if (dragging == pos) offset else 0f }

    /** On the grip only, so the rest of the row still taps and scrolls. */
    fun handle(pos: Int): Modifier = Modifier.pointerInput(pos) {
        detectVerticalDragGestures(
            onDragStart = { dragging = pos; offset = 0f },
            onDragEnd = { onMove(pos, pos + (offset / rowPx).roundToInt()); dragging = null; offset = 0f },
            onDragCancel = { dragging = null; offset = 0f },
            onVerticalDrag = { change, dy -> change.consume(); offset += dy },
        )
    }
}

@Composable
fun rememberReorder(rowHeight: Dp, onMove: (from: Int, to: Int) -> Unit): Reorder {
    val px = with(LocalDensity.current) { rowHeight.toPx() }
    val latest by rememberUpdatedState(onMove)
    return remember(px) { Reorder(px) { from, to -> latest(from, to) } }
}
