package com.galmerom.quickdial.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.galmerom.quickdial.domain.centerSquareCrop
import java.io.File
import java.io.FileOutputStream

class TilePhotos(context: Context) {
    private val directory = File(context.filesDir, "tile-photos").apply { mkdirs() }

    fun importCenterSquare(context: Context, contactId: String, source: Uri, staged: Boolean = false): String? {
        val bitmap = decodeOriented(context, source) ?: return null
        val crop = centerSquareCrop(bitmap.width, bitmap.height) ?: return null
        val square = Bitmap.createBitmap(bitmap, crop.x, crop.y, crop.side, crop.side)
        val scaled = if (square.width <= MAX_EDGE) {
            square
        } else {
            Bitmap.createScaledBitmap(square, MAX_EDGE, MAX_EDGE, true)
        }
        val name = if (staged) stageFileName(contactId) else fileName(contactId)
        FileOutputStream(File(directory, name)).use { out ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)) return null
        }
        if (scaled !== square) scaled.recycle()
        if (square !== bitmap) square.recycle()
        bitmap.recycle()
        return name
    }

    fun fileUri(name: String): String? {
        val file = File(directory, name)
        if (!file.isFile) return null
        return file.toURI().toString()
    }

    fun promoteStaged(stageName: String, contactId: String): String? {
        val source = File(directory, stageName)
        if (!source.isFile) return null
        val destName = fileName(contactId)
        val dest = File(directory, destName)
        if (source.absolutePath != dest.absolutePath) {
            if (dest.exists() && !dest.delete()) return null
            if (!source.renameTo(dest)) {
                source.copyTo(dest, overwrite = true)
                source.delete()
            }
        }
        return destName.takeIf { dest.isFile || File(directory, destName).isFile }
    }

    fun delete(name: String?) {
        if (name.isNullOrBlank()) return
        File(directory, name).delete()
    }

    private fun stageFileName(contactId: String) = "stage-${fileName(contactId)}"

    private fun fileName(contactId: String): String {
        val safe = contactId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "tile" }
        return "$safe.jpg"
    }

    private fun decodeOriented(context: Context, source: Uri): Bitmap? {
        val orientation = context.contentResolver.openFileDescriptor(source, "r")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE * 2)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return applyOrientation(decoded, orientation)
    }

    private companion object {
        const val MAX_EDGE = 1080

        fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= target && sample < 16) {
                sample *= 2
                longest /= 2
            }
            return sample
        }

        fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.preScale(-1f, 1f)
                }
                else -> return bitmap
            }
            val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (turned != bitmap) bitmap.recycle()
            return turned
        }
    }
}
