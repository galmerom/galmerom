package com.galmerom.outfittracker.ui.common

import androidx.annotation.StringRes
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.model.EntityType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@StringRes
fun EntityType.labelRes(): Int = when (this) {
    EntityType.PERSON -> R.string.entity_type_person
    EntityType.FORUM -> R.string.entity_type_forum
}

private val displayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yy")

fun LocalDate.toHebrewDate(): String = format(displayDate)
