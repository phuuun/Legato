package com.phuuun.legato.library.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
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
