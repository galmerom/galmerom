package com.galmerom.outfittracker.domain.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object Dates {
    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromEpochMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun formatIso(date: LocalDate): String = date.toString()

    fun parseIso(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
