package com.phuuun.legato.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/** The three springs everything animates with (Tsundoku's). Nothing has a hard-coded duration. */
object Motion {
    /** Quick with a little bounce: presses. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 800f)

    /** Positions, sizes, layout, slides. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 400f)

    /** Stickers, toggles, the play button's icon. */
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.4f, stiffness = 500f)
}

/** Scales to [to] while pressed and springs back. */
@Composable
fun Modifier.pressScale(pressed: Boolean, to: Float = 0.96f): Modifier {
    val scale by animateFloatAsState(if (pressed) to else 1f, Motion.press(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Tap (and hold, with [onLongClick]) without a ripple: the thing pressed shrinks a little and springs back instead.
 * The feedback every row and control in Legato gives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, enabled: Boolean = true, role: Role? = null, label: String? = null, to: Float = 0.96f): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return pressScale(pressed, to).combinedClickable(source, indication = null, enabled = enabled, onClickLabel = label, role = role, onLongClick = onLongClick, onClick = onClick)
}

/** Wiggles sideways when it first shows, and again whenever [key] changes: a link that didn't work. */
@Composable
fun Modifier.shake(key: Any?): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(key) {
        offset.animateTo(0f, keyframes {
            durationMillis = 300
            6f at 50; -6f at 100; 6f at 150; -6f at 200; 3f at 250; 0f at 300
        })
    }
    return graphicsLayer { translationX = offset.value.dp.toPx() }
}

/** On first composition, settles in from [scale], [offsetY] and transparent. */
@Composable
fun Modifier.enterFrom(scale: Float = 0.92f, offsetY: Dp = 0.dp): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.settle()) }
    return graphicsLayer {
        val p = progress.value
        val s = scale + (1f - scale) * p
        scaleX = s; scaleY = s
        translationY = offsetY.toPx() * (1f - p)
        alpha = p.coerceIn(0f, 1f)
    }
}

/**
 * False on a list's first pass, true right after: rows composed on the first pass [rise] in, rows that scroll in later
 * just appear. Saved, so coming back to a list (from an album, or a swipe away) doesn't replay it.
 */
@Composable
fun rememberSettled(): Boolean {
    var settled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    return settled
}

/** Rises 24dp into place and fades in, 25ms after the row above it (capped at 600ms), unless the list had [settled]. */
@Composable
fun Modifier.rise(settled: Boolean, position: Int): Modifier {
    val arrival = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (arrival.value == 1f) return@LaunchedEffect
        delay(scaled(minOf(position * 25L, 600L)))
        arrival.animateTo(1f, Motion.settle())
    }
    return graphicsLayer {
        translationY = 24.dp.toPx() * (1f - arrival.value)
        alpha = arrival.value.coerceIn(0f, 1f)
    }
}

/** [ms] times the system animator scale, so "animations off" (0) skips delays too. */
suspend fun scaled(ms: Long): Long = (ms * (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)).toLong()

/** Tabs and sheet steps: the new one slides in a third of the way from the side it's on, the old one slides out the other way. */
fun slideStep(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return ContentTransform(
        slideInHorizontally(Motion.settle()) { dir * it / 3 } + fadeIn(Motion.settle()),
        slideOutHorizontally(Motion.settle()) { -dir * it / 3 } + fadeOut(Motion.settle()),
        sizeTransform = SizeTransform(clip = false) { _, _ -> Motion.settle() },
    )
}
