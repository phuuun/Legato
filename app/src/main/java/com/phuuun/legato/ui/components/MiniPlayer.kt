package com.phuuun.legato.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuuun.legato.ui.nowplaying.NowPlayingState
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.Motion
import com.phuuun.legato.ui.theme.tap

private val Card = RoundedCornerShape(20.dp)

/** A card above the bottom bar while anything is queued; tapping it or swiping it up opens the full player, swiping sideways skips. */
@Composable
fun MiniPlayer(state: NowPlayingState, onToggle: () -> Unit, onSkip: (forward: Boolean) -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().tap(onOpen, to = 0.98f).swipeToSkip(onSkip, onUp = onOpen)
            .clip(Card).background(Bold.Surface).border(2.dp, Bold.Ink, Card),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(state.artUri, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundButton(if (state.isPlaying) "Pause" else "Play", onToggle, filled = true) { PlayPauseIcon(state.isPlaying, Modifier.size(24.dp)) }
        }
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp).fillMaxWidth().height(3.dp).clip(Card).background(Bold.QuietLine)) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Bold.Mint))
        }
    }
}

/** Play turns into pause (and back) with a pop instead of a swap. */
@Composable
fun PlayPauseIcon(playing: Boolean, modifier: Modifier) =
    AnimatedContent(
        playing, contentAlignment = Alignment.Center, label = "play pause",
        transitionSpec = { (scaleIn(Motion.pop(), 0.5f) + fadeIn(Motion.settle())) togetherWith (scaleOut(Motion.settle(), 0.5f) + fadeOut(Motion.settle())) },
    ) { on ->
        Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier)
    }
