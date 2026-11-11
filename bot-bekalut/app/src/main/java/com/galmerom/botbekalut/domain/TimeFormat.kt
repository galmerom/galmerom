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
