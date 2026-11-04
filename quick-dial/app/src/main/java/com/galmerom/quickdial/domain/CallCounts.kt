package com.galmerom.quickdial.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class CallDirection {
    Outgoing,
    Incoming,
    Missed,
    Other,
}

data class CallRecord(
    val number: String,
    val direction: CallDirection,
    val atMillis: Long,
)

data class ContactNumbers(
    val id: String,
    val numbers: List<String>,
)

/** Local date-time six months before [nowMillis]. The boundary instant is inclusive. */
fun sixMonthsBefore(nowMillis: Long, zone: ZoneId): Long =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        .minusMonths(6)
        .toInstant()
        .toEpochMilli()

/**
 * Outgoing calls in `[sinceMillis, untilMillis]`, summed per contact across
 * every number that belongs to that contact. A call counts once per contact
 * even if the number is stored twice.
 */
fun outgoingCallCounts(
    calls: List<CallRecord>,
    contacts: List<ContactNumbers>,
    sinceMillis: Long,
    untilMillis: Long,
): Map<String, Int> {
    val keysByContact = contacts.map { contact ->
        contact.id to contact.numbers.map(::phoneKey).filter { it.length >= 7 }.toSet()
    }
    val counts = linkedMapOf<String, Int>()
    for (call in calls) {
        if (call.direction != CallDirection.Outgoing) continue
        if (call.atMillis < sinceMillis || call.atMillis > untilMillis) continue
        val key = phoneKey(call.number)
        if (key.length < 7) continue
        for ((id, keys) in keysByContact) {
            if (key in keys) counts[id] = (counts[id] ?: 0) + 1
        }
    }
    return counts
}
