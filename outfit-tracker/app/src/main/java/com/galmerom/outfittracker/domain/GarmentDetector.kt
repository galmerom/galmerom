package com.galmerom.outfittracker.domain

import com.galmerom.outfittracker.domain.model.DetectedGarment

interface GarmentDetector {
    suspend fun detect(jpeg: ByteArray): Result<List<DetectedGarment>>
}
