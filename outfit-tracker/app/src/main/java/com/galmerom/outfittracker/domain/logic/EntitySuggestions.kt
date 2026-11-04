package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.Entity

object EntitySuggestions {
    fun match(query: String, entities: List<Entity>, limit: Int = 8): List<Entity> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return entities
            .mapNotNull { entity ->
                val index = entity.name.indexOf(needle, ignoreCase = true)
                if (index < 0) null else index to entity
            }
            .sortedWith(compareBy({ it.first }, { it.second.name }))
            .map { it.second }
            .take(limit)
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
