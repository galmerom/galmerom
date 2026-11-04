package com.galmerom.outfittracker.domain.logic

object MajorGarment {
    val wires: List<String> = listOf("pants", "shirt", "skirt", "dress", "jacket")

    fun normalize(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "pants", "trousers", "מכנסיים" -> "pants"
        "shirt", "blouse", "top", "חולצה" -> "shirt"
        "skirt", "חצאית" -> "skirt"
        "dress", "gown", "שמלה" -> "dress"
        "jacket", "coat", "blazer", "מעיל", "ז׳קט", "ז'קט" -> "jacket"
        else -> null
    }

    fun caption(wire: String): String = when (wire) {
        "pants" -> "המכנסיים"
        "shirt" -> "החולצה"
        "skirt" -> "החצאית"
        "dress" -> "השמלה"
        "jacket" -> "המעיל"
        else -> ""
    }

    fun distinct(values: List<String?>): List<String> =
        values.mapNotNull(::normalize).distinct()
}
