package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingHit

object MeetingSearch {
    fun search(meetings: List<Meeting>, entities: List<Entity>, query: String): List<MeetingHit> {
        val needle = query.trim()
        val sorted = meetings.sortedWith(compareByDescending<Meeting> { it.date }.thenByDescending { it.id })
        if (needle.isEmpty()) return sorted.map { MeetingHit(it) }

        val matched = entities.filter { it.name.contains(needle, ignoreCase = true) }
        val personIds = matched.filter { it.type == EntityType.PERSON }.map { it.id }.toSet()
        val forumIds = matched.filter { it.type == EntityType.FORUM }.map { it.id }.toSet()
        val forumsViaPerson = entities.filter { entity ->
            entity.type == EntityType.FORUM && entity.memberIds.any { it in personIds }
        }
        val forumIdsViaPerson = forumsViaPerson.map { it.id }.toSet() - forumIds

        return sorted.mapNotNull { meeting ->
            val direct = meeting.entityId in personIds || meeting.entityId in forumIds
            val viaForum = meeting.entityId in forumIdsViaPerson
            val nameMatch = meeting.entityName.contains(needle, ignoreCase = true)
            when {
                direct || (nameMatch && !viaForum) -> MeetingHit(meeting)
                viaForum -> MeetingHit(meeting, forumName = meeting.entityName)
                nameMatch -> MeetingHit(meeting)
                else -> null
            }
        }.distinctBy { it.meeting.id }
    }
}

fun meetingCounts(meetings: List<Meeting>): Map<String, Int> =
    meetings.groupingBy { it.entityId }.eachCount()
