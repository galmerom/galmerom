package com.galmerom.outfittracker.domain.logic

object ImageSizing {
    const val MAX_EDGE_PX = 1280
    const val AVATAR_EDGE_PX = 512

    fun targetSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 0 to 0
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scaledWidth = (width.toLong() * maxEdge / longest).toInt().coerceAtLeast(1)
        val scaledHeight = (height.toLong() * maxEdge / longest).toInt().coerceAtLeast(1)
        return scaledWidth to scaledHeight
    }

    fun orientedBounds(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees == 90 || rotationDegrees == 270) height to width else width to height

    fun inSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }
}
