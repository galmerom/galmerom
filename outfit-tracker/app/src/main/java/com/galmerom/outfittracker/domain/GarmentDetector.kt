package com.galmerom.outfittracker.domain

import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem

interface GarmentDetector {
    suspend fun detect(
        jpeg: ByteArray,
        items: List<ClothingItem>,
        categories: List<ClothingCategory>,
    ): Result<List<String>>
}
