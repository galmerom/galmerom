package com.galmerom.botbekalut.domain

import java.time.ZoneId

sealed interface ChatRow {
    data class Day(val key: String, val label: String) : ChatRow
    data class Routine(val key: String, val title: String) : ChatRow
    data class Message(val message: ChatMessage) : ChatRow
}

fun buildChatRows(
    messages: List<ChatMessage>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var lastDay: String? = null
    var lastRoutine: String? = null
    messages.forEach { message ->
        val day = localDayKey(message.publishedAt, zone)
        if (day != lastDay) {
            rows += ChatRow.Day(day, formatDay(message.publishedAt, zone))
            lastDay = day
            lastRoutine = null
        }
        val routineKey = message.routineId.ifBlank { message.routineName }
        if (routineKey != lastRoutine) {
            val title = message.routineName.ifBlank { routineKey }
            rows += ChatRow.Routine(key = "$routineKey-$day-${message.id}", title = title)
            lastRoutine = routineKey
        }
        rows += ChatRow.Message(message)
    }
    return rows
}

fun messageIdNear(rows: List<ChatRow>, index: Int): String? {
    if (rows.isEmpty()) return null
    var cursor = index.coerceIn(0, rows.lastIndex)
    while (cursor >= 0) {
        val row = rows[cursor]
        if (row is ChatRow.Message) return row.message.id
        cursor--
    }
    return (rows.firstOrNull { it is ChatRow.Message } as? ChatRow.Message)?.message?.id
}
