package com.galmerom.outfittracker.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.galmerom.outfittracker.domain.logic.ImageSizing
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object ImageCompressor {
    private const val JPEG_QUALITY = 82

    fun jpeg(source: ByteArray, maxEdge: Int = ImageSizing.MAX_EDGE_PX): ByteArray {
        if (source.isEmpty()) return source
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return source

        val rotation = rotationDegrees(source)
        val (orientedWidth, orientedHeight) = ImageSizing.orientedBounds(bounds.outWidth, bounds.outHeight, rotation)
        val (targetWidth, targetHeight) = ImageSizing.targetSize(orientedWidth, orientedHeight, maxEdge)
        if (targetWidth <= 0 || targetHeight <= 0) return source

        val decoded = BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply {
                inSampleSize = ImageSizing.inSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            },
        ) ?: return source

        val rotated = rotate(decoded, rotation)
        val scaled = if (rotated.width == targetWidth && rotated.height == targetHeight) {
            rotated
        } else {
            Bitmap.createScaledBitmap(rotated, targetWidth, targetHeight, true)
        }
        val output = ByteArrayOutputStream()
        val compressed = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        recycleQuietly(scaled, rotated, decoded)
        return if (compressed) output.toByteArray() else source
    }

    private fun rotationDegrees(source: ByteArray): Int {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun recycleQuietly(scaled: Bitmap, rotated: Bitmap, decoded: Bitmap) {
        if (scaled !== rotated && !scaled.isRecycled) scaled.recycle()
        if (rotated !== decoded && !rotated.isRecycled) rotated.recycle()
        if (!decoded.isRecycled) decoded.recycle()
    }
}
