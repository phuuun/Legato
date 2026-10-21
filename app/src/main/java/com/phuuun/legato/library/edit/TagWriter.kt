package com.phuuun.legato.library.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Picked image → JPEG for embedding. ImageDecoder decodes straight to the target size (no full-size bitmap) and applies EXIF rotation. */
suspend fun coverJpeg(context: Context, image: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, image)) { decoder, info, _ ->
        val (w, h) = coverSize(info.size.width, info.size.height)
        decoder.setTargetSize(w, h)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // compress() can't read hardware bitmaps
    }
    ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
}

/** A YouTube thumbnail as a cover: the centre square (thumbnails are 16:9), at most 1000px, JPEG. Null if it can't be had. */
suspend fun coverFromUrl(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = URI(url).toURL().readBytes()
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val scale = minOf(1f, 1000f / minOf(info.size.width, info.size.height))
            val w = (info.size.width * scale).roundToInt()
            val h = (info.size.height * scale).roundToInt()
            decoder.setTargetSize(w, h)
            val c = squareCrop(w, h)
            decoder.crop = Rect(c.left, c.top, c.left + c.size, c.top + c.size)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
    }.getOrNull()
}

/**
 * Writes tags and cover into a file, then has MediaStore re-read it; its id stays the same. Only the converter uses it, on
 * the new file it downloaded: an edit made in Legato stays in Legato (see EditStore).
 */
class TagWriter(private val context: Context) {
    /** [rescan] false for a file that isn't in its final place yet (the converter renames it first). */
    suspend fun write(path: String, properties: Map<String, String>, cover: CoverChange, rescan: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val saved = try {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
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
        if (saved && rescan) withTimeoutOrNull(5_000) { rescan(path) }
        saved
    }

    private suspend fun rescan(path: String) = suspendCancellableCoroutine { done ->
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> done.resume(Unit) }
    }
}
