package com.phuuun.legato.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import com.phuuun.legato.ui.theme.Motion
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Swipe left for the next track, right for the previous; with [onUp], swipe up too. The content follows the finger
 * sideways and springs back. Without [onUp] only sideways drags are taken, so a sheet around it still drags down.
 */
fun Modifier.swipeToSkip(onSkip: (forward: Boolean) -> Unit, onUp: (() -> Unit)? = null): Modifier = composed {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val skip by rememberUpdatedState(onSkip)
    val up by rememberUpdatedState(onUp)
    var drag by remember { mutableStateOf(Offset.Zero) }
    var held by remember { mutableStateOf(false) }
    val x by animateFloatAsState(drag.x, if (held) snap() else Motion.pop(), label = "swipe")
    val start = { _: Offset -> held = true }
    val end = {
        when {
            up != null && -drag.y > threshold && -drag.y > abs(drag.x) -> up?.invoke()
            drag.x <= -threshold -> skip(true)
            drag.x >= threshold -> skip(false)
        }
        held = false
        drag = Offset.Zero
    }
    val cancel = { held = false; drag = Offset.Zero }
    pointerInput(onUp == null) {
        if (onUp == null) detectHorizontalDragGestures(start, end, cancel) { c, dx -> c.consume(); drag += Offset(dx, 0f) }
        else detectDragGestures(start, end, cancel) { c, d -> c.consume(); drag += d }
    }.graphicsLayer { translationX = x / 2 }
}
