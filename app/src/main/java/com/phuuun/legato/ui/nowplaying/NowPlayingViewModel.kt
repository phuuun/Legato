package com.phuuun.legato.ui.nowplaying

import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import com.phuuun.legato.library.Bookmark
import com.phuuun.legato.library.BookmarkStore
import com.phuuun.legato.library.CHAPTERS
import com.phuuun.legato.library.Chapter
import com.phuuun.legato.library.Chapterfy
import com.phuuun.legato.library.EditStore
import com.phuuun.legato.library.asText
import com.phuuun.legato.library.readChapters
import com.phuuun.legato.playback.SleepTimer
import com.phuuun.legato.playback.path
import com.phuuun.legato.playback.coverUri
import com.phuuun.legato.playback.isAudiobook
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

data class NowPlayingState(
    val title: String,
    val artist: String,
    val artUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val shuffle: Boolean,
    val repeatMode: Int,
    val trackId: Long?,
    val isAudiobook: Boolean,
    val speed: Float,
    val path: String?,
)

/** Where a book was before a jump: the chapter's queue index and the spot in it. */
data class BackSpot(val index: Int, val positionMs: Long)

/** Null state means nothing is queued, so the mini player hides. */
@OptIn(UnstableApi::class)
class NowPlayingViewModel(val controller: StateFlow<MediaController?>, val sleep: SleepTimer, private val bookmarkStore: BookmarkStore, private val edits: EditStore, val chapterfy: Chapterfy) : ViewModel() {

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<NowPlayingState?> = controller.filterNotNull().flatMapLatest { c ->
        callbackFlow {
            val l = object : Player.Listener { override fun onEvents(player: Player, events: Player.Events) { trySend(Unit) } }
            c.addListener(l)
            trySend(Unit)
            awaitClose { c.removeListener(l) }
        }.transformLatest {
            // Every player event re-reads the state; while playing, the position ticks on top of that.
            emit(c.snapshot())
            while (c.isPlaying) { delay(500); emit(c.snapshot()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun MediaController.snapshot(): NowPlayingState? {
        val item = currentMediaItem ?: return null
        val m = item.mediaMetadata
        return NowPlayingState(
            m.title?.toString().orEmpty(), m.artist?.toString().orEmpty(), item.coverUri(),
            currentPosition, duration, !Util.shouldShowPlayButton(this), shuffleModeEnabled, repeatMode,
            currentMediaItem?.mediaId?.toLongOrNull(), item.isAudiobook, playbackParameters.speed, item.path(),
        )
    }

    private val c get() = controller.value

    /** The chapters of the book file playing (see readChapters); empty for music. Read once per file, and again when its saved list changes. */
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val chapters: StateFlow<List<Chapter>> = state.map { s -> s?.takeIf { it.isAudiobook }?.path }.distinctUntilChanged()
        .combine(edits.edits) { path, e -> path to path?.let { e[it]?.get(CHAPTERS) } }.distinctUntilChanged()
        .mapLatest { (path, saved) -> path?.let { readChapters(it, c?.currentMediaItem?.mediaMetadata?.durationMs ?: 0, saved) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the book file playing has its own chapter list saved in Legato, which Reset drops. */
    val chaptersSaved: StateFlow<Boolean> = state.map { it?.path }.distinctUntilChanged()
        .combine(edits.edits) { path, e -> path != null && e[path]?.containsKey(CHAPTERS) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveChapters(path: String, chapters: List<Chapter>) { viewModelScope.launch { edits.save(path, mapOf(CHAPTERS to chapters.asText())) } }

    fun resetChapters(path: String) = edits.forget(path, CHAPTERS)

    fun useChapterfy(done: Chapterfy.State.Done) { viewModelScope.launch { chapterfy.use(done) } }

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkStore.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addBookmark(trackId: Long, positionMs: Long, color: Int, caption: String) =
        bookmarkStore.save(Bookmark(System.currentTimeMillis(), trackId, positionMs, color, caption.trim()))

    fun saveBookmark(b: Bookmark) = bookmarkStore.save(b.copy(caption = b.caption.trim()))

    fun deleteBookmark(id: Long) = bookmarkStore.delete(id)

    /** To a chapter or bookmark: [index] in the queue, [ms] into it. Offers the way back like any other jump. */
    fun jumpTo(index: Int, ms: Long) = jump { seekTo(index, ms) }

    // Handles the awkward states too: replays an ended queue, re-prepares after an error.
    fun togglePlay() { c?.let { Util.handlePlayPauseButtonAction(it) } }
    fun next() { c?.seekToNext() }
    fun previous() { c?.seekToPrevious() }
    fun setSpeed(speed: Float) { c?.setPlaybackSpeed(speed) }

    /** A swipe on the artwork or mini player: the next or previous track, or 10 s either way in a book. */
    fun skip(forward: Boolean) {
        val c = c ?: return
        when {
            c.currentMediaItem?.isAudiobook == true -> if (forward) skipForward() else skipBack()
            forward -> c.seekToNextMediaItem()
            else -> c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(ms: Long) = jump { seekTo(ms) }
    fun skipBack() = jump { seekBack() }
    fun skipForward() = jump { seekForward() }

    // In a book, any jump offers the way back to where it started, like Storytel. Jumps in a row keep the first
    // spot, and each one restarts the 5 s before the offer fades.
    private val _backSpot = MutableStateFlow<BackSpot?>(null)
    val backSpot: StateFlow<BackSpot?> = _backSpot
    private var fade: Job? = null

    private fun jump(seek: MediaController.() -> Unit) {
        val c = c ?: return
        if (c.currentMediaItem?.isAudiobook == true) {
            if (_backSpot.value == null) _backSpot.value = BackSpot(c.currentMediaItemIndex, c.currentPosition)
            fade?.cancel()
            fade = viewModelScope.launch { delay(5_000); _backSpot.value = null }
        }
        c.seek()
    }

    fun goBack() {
        _backSpot.value?.let { c?.seekTo(it.index, it.positionMs) }
        fade?.cancel()
        _backSpot.value = null
    }
    fun toggleShuffle() { c?.run { shuffleModeEnabled = !shuffleModeEnabled } }
    fun cycleRepeat() {
        c?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}
