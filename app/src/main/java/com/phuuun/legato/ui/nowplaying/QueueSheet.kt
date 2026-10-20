package com.phuuun.legato.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.phuuun.legato.playback.coverUri
import com.phuuun.legato.playback.moveItem
import com.phuuun.legato.playback.removeItem
import com.phuuun.legato.ui.components.Artwork
import com.phuuun.legato.ui.components.occurrenceKeys
import com.phuuun.legato.ui.components.offerUndo
import com.phuuun.legato.ui.components.rememberReorder
import kotlinx.coroutines.launch

private val RowHeight = 60.dp

/** The queue in play order (shuffle-aware), straight from the player; edits go back through the player. */
private data class Queue(val items: List<MediaItem>, val order: List<Int>, val current: Int, val shuffled: Boolean)

private fun Player.queue(): Queue {
    val t = currentTimeline
    val order = ArrayList<Int>(t.windowCount)
    var i = if (t.isEmpty) C.INDEX_UNSET else t.getFirstWindowIndex(shuffleModeEnabled)
    while (i != C.INDEX_UNSET) { order += i; i = t.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffleModeEnabled) }
    return Queue(List(mediaItemCount) { getMediaItemAt(it) }, order, currentMediaItemIndex, shuffleModeEnabled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: Player, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf(player.queue()) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onEvents(p: Player, e: Player.Events) {
                if (e.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) q = p.queue()
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = q.order.indexOf(q.current).coerceAtLeast(0))
    val reorder = rememberReorder(RowHeight) { from, to -> player.moveItem(from, to) }
    // The sheet is its own window, so it carries its own snackbar; the app's would show underneath.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Text("QUEUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        val keys = remember(q) { occurrenceKeys(q.order) { q.items[it].mediaId } }
        Box {
            LazyColumn(state = list) {
                // Rows fade in and out as they're added or removed; a drag already shows the move, so no slide on reorder.
                itemsIndexed(q.order, key = { pos, _ -> keys[pos] }) { pos, index ->
                    val item = q.items[index]
                    val isCurrent = index == q.current
                    Row(
                        Modifier.animateItem(placementSpec = null).fillMaxWidth().height(RowHeight)
                            .then(reorder.row(pos))
                            .background(if (isCurrent || reorder.dragging == pos) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(item.coverUri(), Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(item.mediaMetadata.title?.toString().orEmpty(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.mediaMetadata.artist?.toString().orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            player.removeItem(index)
                            scope.launch { snackbar.offerUndo("Removed ${item.mediaMetadata.title ?: "track"}") { player.addMediaItem(index.coerceAtMost(player.mediaItemCount), item) } }
                        }) { Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        // Under shuffle the list is play order, not queue order, so a drag has no honest meaning: no handle.
                        if (!q.shuffled) {
                            Icon(Icons.Rounded.DragHandle, "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp).then(reorder.handle(pos)).padding(12.dp))
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
