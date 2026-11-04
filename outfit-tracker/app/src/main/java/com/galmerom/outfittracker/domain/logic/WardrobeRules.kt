package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.DetectedGarment

object WardrobeRules {
    fun selectable(items: List<ClothingItem>): List<ClothingItem> = items.filter { !it.hidden }

    fun match(detected: DetectedGarment, items: List<ClothingItem>): ClothingItem? {
        val needle = detected.description.trim()
        if (needle.isEmpty()) return null
        val candidates = selectable(items).filter { it.categoryId == detected.categoryId }
        return candidates.firstOrNull { it.description.trim().equals(needle, ignoreCase = true) }
            ?: candidates.firstOrNull { item ->
                val hay = item.description.trim()
                hay.length >= 2 && (
                    hay.contains(needle, ignoreCase = true) ||
                        needle.contains(hay, ignoreCase = true)
                    )
            }
    }
}
