package com.galmerom.outfittracker.data.photo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Context.readCompressedJpeg(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("empty image")
    ImageCompressor.jpeg(bytes)
}
