package com.phuuun.legato.ui.nowplaying

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.phuuun.legato.ui.components.Artwork
import kotlinx.coroutines.launch

/** Full-height sheet over everything; swipe down or the chevron drops back to the mini player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(state: NowPlayingState, vm: NowPlayingViewModel, onDismiss: () -> Unit, onEdit: (Long) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var queueOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxSize()) {
            // The cover, blurred and washed over with the background, tints the whole player. Blur needs Android 12.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Artwork(state.artUri, Modifier.matchParentSize().blur(80.dp))
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)))
            }
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    IconButton(onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                    }
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.CenterEnd)) {
                        state.trackId?.let { id ->
                            IconButton(onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); onEdit(id) } }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit info")
                            }
                        }
                        IconButton(onClick = { queueOpen = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue") }
                    }
                }
                Spacer(Modifier.weight(1f))
                Artwork(state.artUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.weight(1f))
                Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Seek(state, vm::seekTo)
                if (state.isAudiobook) GoBack(vm)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Toggle(Icons.Rounded.Shuffle, "Shuffle", state.shuffle, vm::toggleShuffle)
                    // A book skips 10 s instead of chapters; the queue still lists the chapters.
                    if (state.isAudiobook) {
                        IconButton(onClick = vm::skipBack, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", Modifier.size(36.dp)) }
                    } else {
                        IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                    }
                    IconButton(
                        onClick = vm::togglePlay,
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play",
                            Modifier.size(40.dp), tint = MaterialTheme.colorScheme.background,
                        )
                    }
                    if (state.isAudiobook) {
                        IconButton(onClick = vm::skipForward, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(36.dp)) }
                    } else {
                        IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
                    }
                    Toggle(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        state.repeatMode != Player.REPEAT_MODE_OFF, vm::cycleRepeat,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (queueOpen) vm.controller.value?.let { QueueSheet(it, onDismiss = { queueOpen = false }) }
}

/** "Back to 12:34" after a jump in a book; fades on its own. The slot keeps its height so nothing shifts. */
@Composable
private fun GoBack(vm: NowPlayingViewModel) {
    val spot by vm.backSpot.collectAsState()
    // Keep the last spot so the label doesn't blank out while fading.
    var last by remember { mutableStateOf(spot) }
    if (spot != null) last = spot
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(spot != null, enter = fadeIn(), exit = fadeOut()) {
            FilledTonalButton(onClick = vm::goBack) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to ${formatTime(last?.positionMs ?: 0)}")
            }
        }
    }
}

@Composable
private fun Seek(state: NowPlayingState, onSeek: (Long) -> Unit) {
    // While the thumb is held, show where it is, not where the player is; seek once on release.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val canSeek = state.durationMs > 0
    val shown = dragging ?: if (canSeek) state.positionMs.toFloat() / state.durationMs else 0f
    Slider(
        value = shown.coerceIn(0f, 1f), enabled = canSeek,
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { onSeek((it * state.durationMs).toLong()) }; dragging = null },
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onBackground, activeTrackColor = MaterialTheme.colorScheme.onBackground,
            inactiveTrackColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.padding(top = 16.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val t = MaterialTheme.typography.labelMedium
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        Text(formatTime(if (dragging != null) (shown * state.durationMs).toLong() else state.positionMs), style = t, color = c)
        Text(formatTime(state.durationMs), style = t, color = c)
    }
}

/** Shuffle and repeat: lit when on, grey when off. Artwork stays the only colour. */
@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) =
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
