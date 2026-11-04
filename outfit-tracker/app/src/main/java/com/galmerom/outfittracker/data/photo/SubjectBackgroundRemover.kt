package com.galmerom.outfittracker.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.galmerom.outfittracker.domain.BackgroundRemover
import com.galmerom.outfittracker.domain.CutoutResult
import com.galmerom.outfittracker.domain.logic.CutoutQuality
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SubjectBackgroundRemover(
    context: Context,
) : BackgroundRemover {
    private val appContext = context.applicationContext
    private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build(),
    )
    private val installMutex = Mutex()
    private var modelRequested = false

    override suspend fun prepare(jpeg: ByteArray): CutoutResult = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return@withContext CutoutResult(jpeg, null)
        try {
            ensureModel()
            val mask = readMask(bitmap) ?: return@withContext CutoutResult(jpeg, null)
            if (!CutoutQuality.accept(mask, bitmap.width * bitmap.height)) {
                return@withContext CutoutResult(jpeg, null)
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val painted = CutoutQuality.applyWhiteBackground(pixels, mask) ?: return@withContext CutoutResult(jpeg, null)
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            output.setPixels(painted, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val bytes = ByteArrayOutputStream()
            val compressed = output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
            output.recycle()
            if (!compressed) CutoutResult(jpeg, null) else CutoutResult(jpeg, bytes.toByteArray())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "background removal failed", error)
            CutoutResult(jpeg, null)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun ensureModel() {
        if (modelRequested) return
        installMutex.withLock {
            if (modelRequested) return
            val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
            runCatching { ModuleInstall.getClient(appContext).installModules(request).await() }
            modelRequested = true
        }
    }

    private suspend fun readMask(bitmap: Bitmap): FloatArray? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = segmenter.process(image).await()
        val buffer = result.foregroundConfidenceMask ?: return null
        buffer.rewind()
        val values = FloatArray(buffer.remaining())
        if (values.isEmpty()) return null
        buffer.get(values)
        return values
    }

    private companion object {
        const val TAG = "OutfitTracker"
        const val JPEG_QUALITY = 82
    }
}
