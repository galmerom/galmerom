package com.galmerom.outfittracker.domain

import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem

data class WardrobeDetection(
    val matchedIds: List<String> = emptyList(),
    val unmatchedCategories: List<String?> = emptyList(),
)

interface GarmentDetector {
    suspend fun detect(
        jpeg: ByteArray,
        items: List<ClothingItem>,
        categories: List<ClothingCategory>,
    ): Result<WardrobeDetection>
}
