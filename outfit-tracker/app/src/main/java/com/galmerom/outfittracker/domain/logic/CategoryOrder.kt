package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.ClothingCategory

object CategoryOrder {
    /** Order written by the first version of the seed data. */
    val legacySeedIds: List<String> = listOf(
        "shirt",
        "pants",
        "skirt",
        "dress",
        "jacket",
        "shoes",
        "accessory",
        "other",
    )

    /**
     * If the built-in categories are still in the original seed order, return the new
     * sort values (pants, then shirt, then the rest). A custom order is left alone.
     */
    fun migrationUpdates(categories: List<ClothingCategory>): Map<String, Int>? {
        val byId = categories.associateBy { it.id }
        if (legacySeedIds.any { it !in byId }) return null
        val current = legacySeedIds.sortedWith(compareBy({ byId.getValue(it).sortOrder }, { it }))
        if (current != legacySeedIds) return null
        return legacySeedIds
            .sortedBy { id -> preferredRank(id) }
            .mapIndexed { index, id -> id to index }
            .toMap()
    }

    fun sortOrders(ids: List<String>): Map<String, Int> =
        ids.mapIndexed { index, id -> id to index }.toMap()

    private fun preferredRank(id: String): Int = when (id) {
        "pants" -> 0
        "shirt" -> 1
        else -> 2
    }
}
