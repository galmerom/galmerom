package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.Meeting

object MeetingWindow {
    const val PAGE_SIZE = 3

    fun filter(meetings: List<Meeting>, query: String): List<Meeting> {
        val needle = query.trim()
        val matched = if (needle.isEmpty()) {
            meetings
        } else {
            meetings.filter { it.entityName.contains(needle, ignoreCase = true) }
        }
        return matched.sortedWith(compareByDescending<Meeting> { it.date }.thenByDescending { it.id })
    }

    fun visible(filtered: List<Meeting>, pages: Int): List<Meeting> =
        filtered.take(pages.coerceAtLeast(1) * PAGE_SIZE)

    fun hasMore(filtered: List<Meeting>, pages: Int): Boolean =
        filtered.size > pages.coerceAtLeast(1) * PAGE_SIZE
}
