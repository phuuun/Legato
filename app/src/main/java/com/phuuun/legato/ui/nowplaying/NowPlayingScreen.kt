package com.phuuun.legato.ui.nowplaying

import android.os.Build
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.blur
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
                        // A book swaps the queue for its chapters and bookmarks.
                        if (state.isAudiobook) {
                            state.trackId?.let { id ->
                                IconButton(onClick = { marking = id to (vm.controller.value?.currentPosition ?: state.positionMs) }) { Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Add bookmark") }
                            }
                            IconButton(onClick = { bookOpen = true }) { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Chapters and bookmarks") }
                        } else {
                            IconButton(onClick = { queueOpen = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue") }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Artwork(state.artUri, Modifier.fillMaxWidth().aspectRatio(1f).swipeToSkip(vm::skip).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.weight(1f))
                Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                chapters.lastOrNull { it.startMs <= state.positionMs }?.let { c ->
                    Text(c.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).clickable { bookOpen = true })
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
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onBackground, activeTrackColor = MaterialTheme.colorScheme.onBackground,
            inactiveTrackColor = MaterialTheme.colorScheme.outline,
        ),
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
        TextButton(onClick = { open = true }) { Text(label(speed), color = MaterialTheme.colorScheme.onBackground) }
        DropdownMenu(open, { open = false }) {
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
            is SleepTimer.After -> TextButton(onClick = { open = true }) {
                Text("${ceil((m.endsAt - now) / 60_000.0).toInt().coerceAtLeast(1)}m", color = MaterialTheme.colorScheme.onBackground)
            }
            else -> Toggle(Icons.Rounded.Bedtime, "Sleep timer", m == SleepTimer.ChapterEnd) { open = true }
        }
        DropdownMenu(open, { open = false }) {
            listOf(15, 30, 60).forEach { min -> DropdownMenuItem(text = { Text("$min minutes") }, onClick = { timer.after(min); open = false }) }
            DropdownMenuItem(text = { Text("End of chapter") }, onClick = { timer.atChapterEnd(); open = false })
            if (mode != null) DropdownMenuItem(text = { Text("Turn off") }, onClick = { timer.cancel(); open = false })
        }
    }
}

/** Shuffle and repeat: lit when on, grey when off. Artwork stays the only colour. */
@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) =
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
