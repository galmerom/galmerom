package com.galmerom.outfittracker.data.photo

import android.content.Context
import android.net.Uri
import com.galmerom.outfittracker.domain.logic.ImageSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

suspend fun Context.readCompressedJpeg(
    uri: Uri,
    maxEdge: Int = ImageSizing.MAX_EDGE_PX,
): ByteArray = withContext(Dispatchers.IO) {
    var bytes = readBytes(uri)
    if (bytes.isEmpty()) {
        delay(300)
        bytes = readBytes(uri)
    }
    if (bytes.isEmpty()) error("empty image")
    val jpeg = ImageCompressor.jpeg(bytes, maxEdge)
    if (!jpeg.isJpeg()) error("not a jpeg")
    jpeg
}

private fun Context.readBytes(uri: Uri): ByteArray =
    contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()
