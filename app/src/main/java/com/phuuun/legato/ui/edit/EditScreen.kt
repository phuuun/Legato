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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.phuuun.legato.ui.components.screen
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.phuuun.legato.ui.components.FieldShape
import com.phuuun.legato.ui.components.PillButton
import com.phuuun.legato.ui.components.SmallPill
import com.phuuun.legato.ui.components.TapIcon
import com.phuuun.legato.ui.components.fieldFrame
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.enterFrom

/**
 * Edits one file's tags as Legato shows them; the file itself stays as it is (see EditStore). Music: title, artists, album. Audiobooks: book title, chapter title (books
 * with several files only), author, narrator. Both: the cover. A song with no artist opens pre-filled
 * with a guess from its title.
 */
@Composable
fun EditScreen(track: Track?, kind: FolderKind, chapters: Int, onBack: () -> Unit, onSave: (properties: Map<String, String>, cover: CoverChange) -> Unit) {
    if (track == null) {
        Column(Modifier.screen()) { Back(onBack); Empty("Not found", "It may have been deleted.") }
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

    Column(Modifier.screen()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Text("Edit", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val art = Modifier.size(160.dp).enterFrom(scale = 0.9f, offsetY = 8.dp).clip(RoundedCornerShape(20.dp))
                when {
                    preview != null -> Image(preview!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = art)
                    cover == CoverChange.Remove -> Box(art.background(MaterialTheme.colorScheme.surfaceVariant))
                    else -> Artwork(track.artUri, art)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallPill("Change cover", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    SmallPill("Remove cover", { cover = CoverChange.Remove; preview = null })
                }
            }
            if (kind == FolderKind.Music) {
                Field("Title", title) { title = it }
                artists.forEachIndexed { i, a ->
                    Field(if (artists.size == 1) "Artist" else "Artist ${i + 1}", a, onRemove = if (artists.size > 1) fun() { artists.removeAt(i) } else null) { artists[i] = it }
                }
                SmallPill("Add artist", { artists.add("") }, icon = Icons.Rounded.Add)
                Field("Album", album) { album = it }
            } else {
                Field("Book title", bookTitle) { bookTitle = it }
                if (chapters > 1) Field("Chapter title", title) { title = it }
                Field("Author", author) { author = it }
                Field("Narrator", narrator) { narrator = it }
            }
            PillButton("Save", {
                val properties = if (kind == FolderKind.Music) MusicEdit(title, artists, album).properties()
                else BookEdit(bookTitle, title.takeIf { chapters > 1 }, author, narrator).properties()
                onSave(properties, cover)
            }, Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** A text field in the Bold frame (see fieldFrame), with its label inside. */
@Composable
internal fun Field(
    label: String?, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onRemove: (() -> Unit)? = null,
    isError: Boolean = false, singleLine: Boolean = true, onChange: (String) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    TextField(
        value = value, onValueChange = onChange, singleLine = singleLine, maxLines = if (singleLine) 1 else 3, isError = isError,
        label = label?.let { { Text(it) } },
        trailingIcon = onRemove?.let { remove -> { TapIcon(onClick = remove) { Icon(Icons.Rounded.Close, "Remove $label") } } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
            focusedLabelColor = Bold.Ink, cursorColor = Bold.Ink,
        ),
        shape = FieldShape,
        interactionSource = source,
        modifier = modifier.fieldFrame(focused, isError),
    )
}
