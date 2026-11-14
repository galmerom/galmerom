package com.galmerom.botbekalut.domain

import kotlinx.serialization.Serializable

@Serializable
data class ScrollAnchor(
    val messageId: String = "",
    val offset: Int = 0,
)

@Serializable
data class KnownBot(
    val id: String = "",
    val name: String = "",
    val colorHex: String = "",
)

@Serializable
data class AppSettings(
    val themeId: String = ThemeId.Blue.id,
    val fontScale: String = FontSizeId.Large.id,
    val botOrder: List<String> = DEFAULT_BOT_ORDER,
    val lastBotId: String = "",
    val mutedBotIds: List<String> = emptyList(),
    val scrollAnchors: Map<String, ScrollAnchor> = emptyMap(),
    val readCursors: Map<String, Long> = emptyMap(),
    val clearedBefore: Map<String, Long> = emptyMap(),
    val knownBots: List<KnownBot> = emptyList(),
    val askedNotificationPermission: Boolean = false,
    val updatedAt: Long = 0L,
) {
    fun sanitized(): AppSettings = copy(
        themeId = ThemeId.fromId(themeId).id,
        fontScale = FontSizeId.fromId(fontScale).id,
        botOrder = botOrder.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { DEFAULT_BOT_ORDER },
        mutedBotIds = mutedBotIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        knownBots = knownBots.filter { it.id.isNotBlank() }.distinctBy { it.id },
    )
}

enum class ThemeId(val id: String, val label: String) {
    Blue("blue", "כחול"),
    Ocean("ocean", "אוקיינוס"),
    Sage("sage", "מרווה"),
    Lilac("lilac", "לילך"),
    Warm("warm", "חמים"),
    ;

    companion object {
        fun fromId(id: String): ThemeId = entries.firstOrNull { it.id == id } ?: Blue
    }
}

enum class FontSizeId(val id: String, val label: String, val multiplier: Float) {
    Small("small", "קטן", 0.92f),
    Medium("medium", "בינוני", 1f),
    Large("large", "גדול", 1.14f),
    Extra("xlarge", "גדול מאוד", 1.3f),
    ;

    companion object {
        fun fromId(id: String): FontSizeId = entries.firstOrNull { it.id == id } ?: Large
    }
}
