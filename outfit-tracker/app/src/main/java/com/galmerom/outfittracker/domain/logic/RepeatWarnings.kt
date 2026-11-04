package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.Meeting
import java.time.LocalDate

data class RepeatWarning(
    val entityName: String,
    val date: LocalDate,
    val extraCount: Int,
)

object RepeatWarnings {
    fun relatedIds(selectedIds: Set<String>, entities: List<Entity>): Set<String> {
        if (selectedIds.isEmpty()) return emptySet()
        val selected = entities.filter { it.id in selectedIds }
        val ids = selectedIds.toMutableSet()
        val personIds = selected.filter { it.type == EntityType.PERSON }.map { it.id }.toSet()
        entities.filter { entity ->
            entity.type == EntityType.FORUM && entity.memberIds.any { it in personIds }
        }.forEach { ids += it.id }
        selected.filter { it.type == EntityType.FORUM }.forEach { forum ->
            ids += forum.memberIds
        }
        return ids
    }

    fun forSelection(
        selectedEntityIds: Set<String>,
        selectedItemIds: Set<String>,
        entities: List<Entity>,
        meetings: List<Meeting>,
        excludeMeetingId: String? = null,
    ): Map<String, RepeatWarning> {
        val related = relatedIds(selectedEntityIds, entities)
        if (related.isEmpty() || selectedItemIds.isEmpty()) return emptyMap()
        return selectedItemIds.mapNotNull { itemId ->
            val matches = meetings.filter { meeting ->
                !meeting.hidden &&
                    meeting.id != excludeMeetingId &&
                    itemId in meeting.clothingItemIds &&
                    meeting.entityIds.any { it in related }
            }.sortedWith(compareByDescending<Meeting> { it.date }.thenByDescending { it.id })
            val latest = matches.firstOrNull() ?: return@mapNotNull null
            val overlapId = latest.entityIds.firstOrNull { it in related }
            val name = entities.firstOrNull { it.id == overlapId }?.name
                ?: latest.entityNames.zip(latest.entityIds).firstOrNull { it.second == overlapId }?.first
                ?: latest.displayNames()
            itemId to RepeatWarning(
                entityName = name.ifBlank { overlapId.orEmpty() },
                date = latest.date,
                extraCount = (matches.size - 1).coerceAtLeast(0),
            )
        }.toMap()
    }
}
