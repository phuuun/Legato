package com.phuuun.legato.ui.nowplaying

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.phuuun.legato.playback.SleepTimer
import com.phuuun.legato.ui.components.Artwork
import com.phuuun.legato.ui.components.swipeToSkip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.phuuun.legato.ui.components.BoldMenu
import com.phuuun.legato.ui.components.PlayPauseIcon
import com.phuuun.legato.ui.components.RoundButton
import com.phuuun.legato.ui.components.SmallPill
import com.phuuun.legato.ui.components.TapIcon
import com.phuuun.legato.ui.components.boldSliderColors
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.Motion
import com.phuuun.legato.ui.theme.enterFrom
import com.phuuun.legato.ui.theme.pressScale
import com.phuuun.legato.ui.theme.tap
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlinx.coroutines.launch

/** Full-height sheet over everything; swipe down or the chevron drops back to the mini player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(state: NowPlayingState, vm: NowPlayingViewModel, onDismiss: () -> Unit, onEdit: (Long) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var queueOpen by remember { mutableStateOf(false) }
    var bookOpen by remember { mutableStateOf(false) }
    // Where a new bookmark goes: the file and spot when the button was tapped, not when the dialog is saved.
    var marking by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val chapters by vm.chapters.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Bold.Paper,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    // At most two buttons a side, so neither reaches the title in the middle.
                    Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundButton("Close", { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        }
                        state.trackId?.let { id ->
                            RoundButton("Edit info", { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); onEdit(id) } }) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, Modifier.size(20.dp))
                            }
                        }
                    }
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // A book swaps the queue for its chapters and bookmarks.
                        if (state.isAudiobook) {
                            state.trackId?.let { id ->
                                RoundButton("Add bookmark", { marking = id to (vm.controller.value?.currentPosition ?: state.positionMs) }) { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, Modifier.size(20.dp)) }
                            }
                            RoundButton("Chapters and bookmarks", { bookOpen = true }) { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, Modifier.size(20.dp)) }
                        } else {
                            RoundButton("Queue", { queueOpen = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, Modifier.size(20.dp)) }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Settles in on open, and sits back a little while paused.
                val artScale by animateFloatAsState(if (state.isPlaying) 1f else 0.88f, Motion.settle(), label = "art")
                Artwork(
                    state.artUri,
                    Modifier.fillMaxWidth().aspectRatio(1f).enterFrom(scale = 0.9f, offsetY = 16.dp)
                        .graphicsLayer { scaleX = artScale; scaleY = artScale }.swipeToSkip(vm::skip).clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.weight(1f))
                Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                chapters.lastOrNull { it.startMs <= state.positionMs }?.let { c ->
                    Text(c.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).tap({ bookOpen = true }))
                }
                Seek(state, vm::seekTo) {
                    if (state.isAudiobook) Marks(bookmarks.filter { it.trackId == state.trackId }.map { it.positionMs to bookmarkColor(it.color) }, state.durationMs)
                }
                if (state.isAudiobook) GoBack(vm)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // A book has speed and a sleep timer where shuffle and repeat would be, and skips 10 s instead of
                    // chapters; the queue still lists the chapters.
                    if (state.isAudiobook) Speed(state.speed, vm::setSpeed)
                    else Toggle(Icons.Rounded.Shuffle, "Shuffle", state.shuffle, vm::toggleShuffle)
                    if (state.isAudiobook) {
                        TapIcon(onClick = vm::skipBack, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", Modifier.size(36.dp)) }
                    } else {
                        TapIcon(onClick = vm::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                    }
                    PlayButton(state.isPlaying, vm::togglePlay)
                    if (state.isAudiobook) {
                        TapIcon(onClick = vm::skipForward, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(36.dp)) }
                    } else {
                        TapIcon(onClick = vm::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
                    }
                    if (state.isAudiobook) Sleep(vm.sleep)
                    else Toggle(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        state.repeatMode != Player.REPEAT_MODE_OFF, vm::cycleRepeat,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (queueOpen) vm.controller.value?.let { QueueSheet(it, onDismiss = { queueOpen = false }) }
    if (bookOpen) BookSheet(state, vm, onDismiss = { bookOpen = false })
    marking?.let { (trackId, ms) ->
        BookmarkDialog(
            "Bookmark at ${formatTime(ms)}", "", 0,
            onSave = { caption, color -> vm.addBookmark(trackId, ms, color, caption) }, onDelete = null, onDismiss = { marking = null },
        )
    }
}

/** The big Mint button: shrinks a little when pressed, and its icon pops between play and pause. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier.size(76.dp).pressScale(pressed).clip(CircleShape).background(Bold.Mint).border(2.dp, Bold.Paper, CircleShape)
            .semantics { contentDescription = if (playing) "Pause" else "Play" }
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides Bold.DarkInk) { PlayPauseIcon(playing, Modifier.size(40.dp)) } }
}

/** "Back to 12:34" after a jump in a book; pops in and fades on its own. The slot keeps its height so nothing shifts. */
@Composable
private fun GoBack(vm: NowPlayingViewModel) {
    val spot by vm.backSpot.collectAsState()
    // Keep the last spot so the label doesn't blank out while fading.
    var last by remember { mutableStateOf(spot) }
    if (spot != null) last = spot
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(spot != null, enter = fadeIn(Motion.settle()) + scaleIn(Motion.pop(), 0.8f), exit = fadeOut(Motion.settle()) + scaleOut(Motion.settle(), 0.9f)) {
            SmallPill("Back to ${formatTime(last?.positionMs ?: 0)}", vm::goBack, icon = Icons.AutoMirrored.Rounded.Undo)
        }
    }
}

/** Coloured dots under the seek bar where this file's bookmarks are. Inset like the slider's track. */
@Composable
private fun Marks(marks: List<Pair<Long, Color>>, durationMs: Long) {
    if (durationMs <= 0 || marks.isEmpty()) return
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(8.dp)) {
        marks.forEach { (ms, color) -> drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width * (ms.toFloat() / durationMs).coerceIn(0f, 1f), size.height / 2)) }
    }
}

@Composable
private fun Seek(state: NowPlayingState, onSeek: (Long) -> Unit, below: @Composable () -> Unit = {}) {
    // While the thumb is held, show where it is, not where the player is; seek once on release.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val canSeek = state.durationMs > 0
    val shown = dragging ?: if (canSeek) state.positionMs.toFloat() / state.durationMs else 0f
    Slider(
        value = shown.coerceIn(0f, 1f), enabled = canSeek,
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { onSeek((it * state.durationMs).toLong()) }; dragging = null },
        colors = boldSliderColors(),
        modifier = Modifier.padding(top = 16.dp),
    )
    below()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val t = MaterialTheme.typography.labelMedium
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        Text(formatTime(if (dragging != null) (shown * state.durationMs).toLong() else state.positionMs), style = t, color = c)
        Text(formatTime(state.durationMs), style = t, color = c)
    }
}

private val Speeds = listOf(0.8f, 1f, 1.2f, 1.5f, 1.75f, 2f)

/** "1.5×"; tapping it lists the speeds. */
@Composable
private fun Speed(speed: Float, onPick: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = { s: Float -> s.toString().removeSuffix(".0") + "×" }
    Box {
        SmallPill(label(speed), { open = true })
        BoldMenu(open, { open = false }) {
            Speeds.forEach { s -> DropdownMenuItem(text = { Text(label(s)) }, onClick = { onPick(s); open = false }) }
        }
    }
}

/** A moon, or the minutes left once set; tapping it lists the choices. */
@Composable
private fun Sleep(timer: SleepTimer) {
    val mode by timer.mode.collectAsState()
    var open by remember { mutableStateOf(false) }
    // The minutes left tick on their own, not with the player.
    val now by produceState(SystemClock.elapsedRealtime(), mode) { while (true) { value = SystemClock.elapsedRealtime(); delay(1_000) } }
    Box {
        when (val m = mode) {
            is SleepTimer.After -> SmallPill("${ceil((m.endsAt - now) / 60_000.0).toInt().coerceAtLeast(1)}m", { open = true })
            else -> Toggle(Icons.Rounded.Bedtime, "Sleep timer", m == SleepTimer.ChapterEnd) { open = true }
        }
        BoldMenu(open, { open = false }) {
            listOf(15, 30, 60).forEach { min -> DropdownMenuItem(text = { Text("$min minutes") }, onClick = { timer.after(min); open = false }) }
            DropdownMenuItem(text = { Text("End of chapter") }, onClick = { timer.atChapterEnd(); open = false })
            if (mode != null) DropdownMenuItem(text = { Text("Turn off") }, onClick = { timer.cancel(); open = false })
        }
    }
}

/** Shuffle, repeat and the sleep timer: Mint when on, grey when off, with a pop when switched. */
@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) Bold.Mint else Bold.Muted.copy(alpha = 0.6f), Motion.settle(), label = "toggle")
    val pop = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(on) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        pop.snapTo(0.7f)
        pop.animateTo(1f, Motion.pop())
    }
    TapIcon(onClick = onClick, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { Icon(icon, label, tint = tint) }
}
