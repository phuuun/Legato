package com.phuuun.legato.library.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.phuuun.legato.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/** Picked image → JPEG for embedding. ImageDecoder decodes straight to the target size (no full-size bitmap) and applies EXIF rotation. */
suspend fun coverJpeg(context: Context, image: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, image)) { decoder, info, _ ->
        val (w, h) = coverSize(info.size.width, info.size.height)
        decoder.setTargetSize(w, h)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // compress() can't read hardware bitmaps
    }
    ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
}

/** Writes tags and cover into the file itself (all files access), then has MediaStore re-read it; its id stays the same. */
class TagWriter(private val context: Context) {
    suspend fun write(track: Track, properties: Map<String, String>, cover: CoverChange): Boolean = withContext(Dispatchers.IO) {
        val saved = try {
            ParcelFileDescriptor.open(File(track.path), ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                // Each TagLib call takes ownership of its own duplicate descriptor.
                val map = TagLib.getMetadata(fd.dup().detachFd(), false)?.propertyMap ?: return@use false
                for ((key, value) in properties) if (value.isBlank()) map.remove(key) else map[key] = arrayOf(value)
                TagLib.savePropertyMap(fd.dup().detachFd(), map) && when (cover) {
                    CoverChange.Keep -> true
                    CoverChange.Remove -> TagLib.savePictures(fd.dup().detachFd(), emptyArray())
                    is CoverChange.Set -> TagLib.savePictures(fd.dup().detachFd(), arrayOf(Picture(cover.jpeg, "Front Cover", "Front Cover", "image/jpeg")))
                }
            }
        } catch (e: Exception) {
            false
        }
        // The file is saved either way; a scanner that never calls back mustn't hang the editor.
        if (saved) withTimeoutOrNull(5_000) { rescan(track.path) }
        saved
    }

    private suspend fun rescan(path: String) = suspendCancellableCoroutine { done ->
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> done.resume(Unit) }
    }
}
