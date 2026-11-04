package com.galmerom.outfittracker.domain.logic

object CutoutQuality {
    const val CONFIDENCE = 0.5f
    const val MIN_FOREGROUND = 0.02f
    const val MAX_FOREGROUND = 0.92f

    fun foregroundFraction(mask: FloatArray, threshold: Float = CONFIDENCE): Float {
        if (mask.isEmpty()) return 0f
        var foreground = 0
        for (value in mask) {
            if (value >= threshold) foreground++
        }
        return foreground.toFloat() / mask.size
    }

    fun accept(mask: FloatArray?, pixelCount: Int): Boolean {
        if (mask == null || mask.isEmpty() || mask.size != pixelCount || pixelCount <= 0) return false
        val fraction = foregroundFraction(mask)
        return fraction in MIN_FOREGROUND..MAX_FOREGROUND
    }

    fun applyWhiteBackground(pixels: IntArray, mask: FloatArray, threshold: Float = CONFIDENCE): IntArray? {
        if (pixels.isEmpty() || pixels.size != mask.size) return null
        val white = 0xFFFFFFFF.toInt()
        return IntArray(pixels.size) { index ->
            if (mask[index] >= threshold) pixels[index] else white
        }
    }
}
