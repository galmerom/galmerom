package com.galmerom.outfittracker.domain.model

import java.time.LocalDate

data class ClothingCategory(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

object DefaultCategories {
    val seeds: List<ClothingCategory> = listOf(
        ClothingCategory("shirt", "חולצה", 0),
        ClothingCategory("pants", "מכנסיים", 1),
        ClothingCategory("skirt", "חצאית", 2),
        ClothingCategory("dress", "שמלה", 3),
        ClothingCategory("jacket", "ז׳קט", 4),
        ClothingCategory("shoes", "נעליים", 5),
        ClothingCategory("accessory", "אביזר", 6),
        ClothingCategory("other", "אחר", 7),
    )

    fun fromLegacy(value: String?): String? {
        val wire = value?.trim()?.lowercase().orEmpty()
        if (wire.isEmpty()) return null
        return seeds.firstOrNull { it.id == wire }?.id
    }
}

enum class EntityType {
    PERSON,
    FORUM,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): EntityType? =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() }
    }
}

data class Entity(
    val id: String,
    val name: String,
    val type: EntityType,
    val hidden: Boolean = false,
    val memberIds: List<String> = emptyList(),
)

data class ClothingItem(
    val id: String,
    val description: String,
    val categoryId: String,
    val photoUrl: String?,
    val hidden: Boolean,
)

data class Meeting(
    val id: String,
    val date: LocalDate,
    val entityIds: List<String>,
    val entityNames: List<String>,
    val clothingItemIds: List<String>,
    val photoUrl: String?,
    val note: String,
    val hidden: Boolean = false,
) {
    val entityId: String get() = entityIds.firstOrNull().orEmpty()
    val entityName: String get() = displayNames()

    fun displayNames(): String = entityNames.filter { it.isNotBlank() }.joinToString(", ")
}

data class MeetingDraft(
    val date: LocalDate,
    val entityIds: List<String>,
    val entityNames: List<String>,
    val clothingItemIds: List<String>,
    val note: String,
)

data class DetectedGarment(
    val categoryId: String,
    val description: String,
)

data class MeetingHit(
    val meeting: Meeting,
    val forumName: String? = null,
)

enum class ErrorKind {
    PERMISSION,
    OTHER,
}

enum class SaveOutcome {
    SAVED,
    SAVED_WITHOUT_PHOTO,
}
