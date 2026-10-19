package com.phuuun.legato.ui.edit

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phuuun.legato.library.FolderKind
import com.phuuun.legato.library.Track
import com.phuuun.legato.library.albumTag
import com.phuuun.legato.library.edit.BookEdit
import com.phuuun.legato.library.edit.CoverChange
import com.phuuun.legato.library.edit.MusicEdit
import com.phuuun.legato.library.edit.coverJpeg
import com.phuuun.legato.library.edit.guessFromTitle
import com.phuuun.legato.library.edit.properties
import com.phuuun.legato.ui.components.Artwork
import com.phuuun.legato.ui.library.Back
import com.phuuun.legato.ui.library.Empty
import kotlinx.coroutines.launch

/**
 * Edits the tags in one file. Music: title, artists, album. Audiobooks: book title, chapter title (books
 * with several files only), author, narrator. Both: the cover. A song with no artist opens pre-filled
 * with a guess from its title.
 */
@Composable
fun EditScreen(track: Track?, kind: FolderKind, chapters: Int, onBack: () -> Unit, onSave: (properties: Map<String, String>, cover: CoverChange) -> Unit) {
    if (track == null) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) { Back(onBack); Empty("Not found", "It may have been deleted.") }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by remember { mutableStateOf<CoverChange>(CoverChange.Keep) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { image ->
        if (image != null) scope.launch {
            val jpeg = coverJpeg(context, image)
            cover = CoverChange.Set(jpeg)
            preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).asImageBitmap()
        }
    }

    val guess = remember(track.id) { if (kind == FolderKind.Music && track.artists.isEmpty()) guessFromTitle(track.title) else null }
    var title by rememberSaveable { mutableStateOf(guess?.title ?: track.title) }
    val artists = remember { (guess?.artist?.takeIf { it.isNotBlank() }?.let(::listOf) ?: track.artists).ifEmpty { listOf("") }.toMutableStateList() }
    var album by rememberSaveable { mutableStateOf(track.albumTag) }
    var bookTitle by rememberSaveable { mutableStateOf(track.albumTag.ifEmpty { track.title }) }
    var author by rememberSaveable { mutableStateOf(track.artists.joinToString("; ")) }
    var narrator by rememberSaveable { mutableStateOf(track.composer) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Text("Edit", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val properties = if (kind == FolderKind.Music) MusicEdit(title, artists, album).properties()
                else BookEdit(bookTitle, title.takeIf { chapters > 1 }, author, narrator).properties()
                onSave(properties, cover)
            }, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val art = Modifier.size(160.dp).clip(RoundedCornerShape(10.dp))
                when {
                    preview != null -> Image(preview!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = art)
                    cover == CoverChange.Remove -> Box(art.background(MaterialTheme.colorScheme.surfaceVariant))
                    else -> Artwork(track.artUri, art)
                }
                Column {
                    TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Change cover") }
                    TextButton(onClick = { cover = CoverChange.Remove; preview = null }) { Text("Remove cover") }
                }
            }
            if (kind == FolderKind.Music) {
                Field("Title", title) { title = it }
                artists.forEachIndexed { i, a ->
                    Field(if (artists.size == 1) "Artist" else "Artist ${i + 1}", a, onRemove = if (artists.size > 1) fun() { artists.removeAt(i) } else null) { artists[i] = it }
                }
                TextButton(onClick = { artists.add("") }) { Icon(Icons.Rounded.Add, null); Text("Add artist", Modifier.padding(start = 8.dp)) }
                Field("Album", album) { album = it }
            } else {
                Field("Book title", bookTitle) { bookTitle = it }
                if (chapters > 1) Field("Chapter title", title) { title = it }
                Field("Author", author) { author = it }
                Field("Narrator", narrator) { narrator = it }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onRemove: (() -> Unit)? = null, onChange: (String) -> Unit) =
    TextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        trailingIcon = onRemove?.let { remove -> { IconButton(onClick = remove) { Icon(Icons.Rounded.Close, "Remove $label") } } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
