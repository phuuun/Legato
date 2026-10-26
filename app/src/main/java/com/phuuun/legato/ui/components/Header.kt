package com.phuuun.legato.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every full screen's frame: clear of the status bar AND the camera cutout (on some phones the punch-hole
 * reaches below the status bar), the keyboard when it's up, plus room to breathe under the top edge.
 */
fun Modifier.screen(): Modifier = fillMaxSize().safeDrawingPadding().padding(top = 12.dp)

/**
 * Top of the main screen, like Tsundoku's: the wordmark and settings, then the big section title with its count
 * sticker. Both follow the pager: [position] is the page, fractional mid-swipe.
 */
@Composable
fun LegatoHeader(titles: List<String>, counts: List<Int?>, position: Float, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("legato", color = Bold.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            RoundButton("Settings", onSettings, filled = true) { Icon(Icons.Outlined.Settings, contentDescription = null, Modifier.size(22.dp)) }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTitle(titles, position)
            counts[position.roundToInt().coerceIn(0, counts.lastIndex)]?.let { CountSticker(it) }
        }
    }
}

/** Every title, each sliding and fading by how far the pager is from its page; the row is as wide as the one showing. */
@Composable
private fun BigTitle(titles: List<String>, position: Float) {
    Layout(content = {
        titles.forEachIndexed { i, name ->
            Text(
                name, style = MaterialTheme.typography.headlineLarge, color = Bold.Ink, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer {
                    translationX = (i - position) * 60.dp.toPx()
                    alpha = (1f - abs(i - position)).coerceIn(0f, 1f)
                },
            )
        }
    }) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val from = position.toInt().coerceIn(0, placeables.lastIndex)
        val to = (from + 1).coerceAtMost(placeables.lastIndex)
        val width = (placeables[from].width + (placeables[to].width - placeables[from].width) * (position - from)).roundToInt()
        layout(width, placeables.maxOf { it.height }) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

/** The green tilted number: pops and wobbles whenever [count] changes. */
@Composable
private fun CountSticker(count: Int) {
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(-10f) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count == shown) return@LaunchedEffect
        shown = count
        scale.snapTo(1.25f)
        tilt.snapTo(-18f)
        launch { scale.animateTo(1f, Motion.pop()) }
        tilt.animateTo(-10f, Motion.pop())
    }
    Box(
        Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = tilt.value }
            .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
            .background(Bold.Mint, CircleShape)
            .border(2.dp, Bold.Paper, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = Bold.DarkInk, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}
