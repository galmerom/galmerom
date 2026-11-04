package com.galmerom.outfittracker.domain.model

import java.time.LocalDate

enum class ClothingCategory {
    SHIRT,
    PANTS,
    SKIRT,
    DRESS,
    JACKET,
    SHOES,
    ACCESSORY,
    OTHER,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): ClothingCategory? =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() }
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
)

data class ClothingItem(
    val id: String,
    val description: String,
    val category: ClothingCategory,
    val photoUrl: String?,
    val hidden: Boolean,
)

data class Meeting(
    val id: String,
    val date: LocalDate,
    val entityId: String,
    val entityName: String,
    val clothingItemIds: List<String>,
    val photoUrl: String?,
    val note: String,
)

data class MeetingDraft(
    val date: LocalDate,
    val entityId: String,
    val entityName: String,
    val clothingItemIds: List<String>,
    val note: String,
)

data class DetectedGarment(
    val category: ClothingCategory,
    val description: String,
)

enum class ErrorKind {
    PERMISSION,
    OTHER,
}

enum class SaveOutcome {
    SAVED,
    SAVED_WITHOUT_PHOTO,
}
