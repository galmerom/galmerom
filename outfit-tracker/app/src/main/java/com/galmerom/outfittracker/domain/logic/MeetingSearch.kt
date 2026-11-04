package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingHit

object MeetingSearch {
    fun search(
        meetings: List<Meeting>,
        entities: List<Entity>,
        query: String,
        includeHidden: Boolean = false,
    ): List<MeetingHit> {
        val visible = if (includeHidden) meetings else meetings.filter { !it.hidden }
        val needle = query.trim()
        val sorted = visible.sortedWith(compareByDescending<Meeting> { it.date }.thenByDescending { it.id })
        if (needle.isEmpty()) return sorted.map { MeetingHit(it) }

        val matched = entities.filter { it.name.contains(needle, ignoreCase = true) }
        val personIds = matched.filter { it.type == EntityType.PERSON }.map { it.id }.toSet()
        val forumIds = matched.filter { it.type == EntityType.FORUM }.map { it.id }.toSet()
        val forumIdsViaPerson = entities.filter { entity ->
            entity.type == EntityType.FORUM && entity.memberIds.any { it in personIds }
        }.map { it.id }.toSet() - forumIds

        return sorted.mapNotNull { meeting ->
            val ids = meeting.entityIds.toSet()
            val direct = ids.any { it in personIds || it in forumIds }
            val viaForum = ids.any { it in forumIdsViaPerson }
            val nameMatch = meeting.entityNames.any { it.contains(needle, ignoreCase = true) } ||
                meeting.displayNames().contains(needle, ignoreCase = true)
            when {
                direct || (nameMatch && !viaForum) -> MeetingHit(meeting)
                viaForum -> {
                    val forumName = meeting.entityNames.zip(meeting.entityIds)
                        .firstOrNull { it.second in forumIdsViaPerson }
                        ?.first
                        ?.ifBlank { null }
                        ?: entities.firstOrNull { it.id in ids && it.id in forumIdsViaPerson }?.name
                    MeetingHit(meeting, forumName = forumName)
                }
                nameMatch -> MeetingHit(meeting)
                else -> null
            }
        }.distinctBy { it.meeting.id }
    }
}

fun meetingCounts(meetings: List<Meeting>): Map<String, Int> =
    meetings.filter { !it.hidden }
        .flatMap { it.entityIds }
        .groupingBy { it }
        .eachCount()
