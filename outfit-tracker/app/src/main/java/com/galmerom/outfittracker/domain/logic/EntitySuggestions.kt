package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.Entity

object EntitySuggestions {
    fun selectable(entities: List<Entity>): List<Entity> = entities.filter { !it.hidden }

    fun forPicker(
        query: String,
        entities: List<Entity>,
        meetingCounts: Map<String, Int>,
        limit: Int = 12,
    ): List<Entity> {
        val needle = query.trim()
        val pool = selectable(entities).filter { entity ->
            needle.isEmpty() || entity.name.contains(needle, ignoreCase = true)
        }
        return pool
            .sortedWith(compareByDescending<Entity> { meetingCounts[it.id] ?: 0 }.thenBy { it.name })
            .let { ranked -> if (needle.isEmpty()) ranked.take(limit) else ranked.take(limit) }
    }

    fun match(query: String, entities: List<Entity>, limit: Int = 8): List<Entity> =
        forPicker(query, entities, emptyMap(), limit).let { ranked ->
            val needle = query.trim()
            if (needle.isEmpty()) emptyList() else ranked
        }

    fun hasExactMatch(query: String, entities: List<Entity>): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return false
        return entities.any { it.name.equals(needle, ignoreCase = true) }
    }

    fun shouldOfferCreate(query: String, entities: List<Entity>): Boolean {
        val needle = query.trim()
        return needle.isNotEmpty() && !hasExactMatch(needle, entities)
    }
}
