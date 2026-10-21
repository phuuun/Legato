package com.phuuun.legato.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * A track's cover: the file's own (see [AudioCover]), or a cover picked in Edit, kept as a file of Legato's. The
 * `surfaceVariant` box behind it is the placeholder when a track has no art.
 */
@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (uri != null) AsyncImage(model = if (uri.scheme == "file") uri else AudioCover(uri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}
