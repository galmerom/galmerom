package com.galmerom.outfittracker.domain

import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.DetectedGarment

interface GarmentDetector {
    suspend fun detect(jpeg: ByteArray, categories: List<ClothingCategory>): Result<List<DetectedGarment>>
}
