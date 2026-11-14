package com.galmerom.botbekalut.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hebrew = Locale.forLanguageTag("he")
private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", hebrew)
private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d 'ב'MMMM", hebrew)

fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(clockFormatter)

fun formatDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(dayFormatter)

fun localDayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

private val israelZone: ZoneId = ZoneId.of("Asia/Jerusalem")
private val shortDateFormatter = DateTimeFormatter.ofPattern("d.M", hebrew)

/** Broadcast time in Israel time: "HH:mm", plus "d.M" when the message is not from today. */
fun formatBroadcastStamp(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(israelZone)
    val clock = moment.format(clockFormatter)
    val today = Instant.ofEpochMilli(now).atZone(israelZone).toLocalDate()
    return if (moment.toLocalDate() == today) clock else "$clock  \u00B7  ${moment.format(shortDateFormatter)}"
}
