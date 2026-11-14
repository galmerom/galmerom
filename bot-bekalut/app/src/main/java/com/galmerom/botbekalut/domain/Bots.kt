package com.galmerom.botbekalut.domain

const val ALLOWED_EMAIL = "energy2@gmail.com"
const val WEB_CLIENT_ID = "797629258178-bg0gokehgjnhubeq10iv3fuj38vm8uls.apps.googleusercontent.com"

val DEFAULT_BOT_ORDER = listOf("ilan", "analyst", "ayelet", "groki")

private val fallbackNames = mapOf(
    "ilan" to "אילן",
    "analyst" to "האנליסט",
    "ayelet" to "איילת",
    "groki" to "גרוקי",
)

private val fallbackColors = mapOf(
    "ilan" to 0x1565C0,
    "analyst" to 0x2E7D32,
    "ayelet" to 0x6A1B9A,
    "groki" to 0xEF6C00,
)

private val derivedPalette = listOf(
    0x00838F,
    0xC62828,
    0x455A64,
    0xAD1457,
    0x6D4C41,
    0x0277BD,
    0x558B2F,
    0xF9A825,
)

private val topicId = Regex("^[A-Za-z0-9_-]+$")

data class BotButton(
    val id: String,
    val name: String,
    val colorRgb: Int,
    val hasUnread: Boolean,
)

fun fallbackName(botId: String): String = fallbackNames[botId] ?: botId

fun fallbackColor(botId: String): Int {
    fallbackColors[botId]?.let { return it }
    val hash = botId.fold(0) { acc, char -> acc * 31 + char.code }
    return derivedPalette[(hash and Int.MAX_VALUE) % derivedPalette.size]
}

fun parseHexColor(raw: String?): Int? {
    val hex = raw?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6) return null
    return hex.toIntOrNull(16)
}

fun rgbToHex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

fun topicFor(botId: String): String? {
    if (!topicId.matches(botId)) return null
    return "bot_$botId"
}

fun visibleBots(
    messages: List<ChatMessage>,
    order: List<String>,
    readCursors: Map<String, Long>,
    now: Long,
): List<BotButton> {
    val recent = messages.filter { inDisplayWindow(it.publishedAt, now) && it.botId.isNotBlank() }
    val byId = recent.groupBy { it.botId }
    val rank = order.withIndex().associate { it.value to it.index }
    return byId.keys.sortedWith(
        compareBy<String> { rank[it] ?: Int.MAX_VALUE }
            .thenBy { byId.getValue(it).lastOrNull()?.botName.orEmpty() }
            .thenBy { it },
    ).map { id ->
        val items = byId.getValue(id).sortedBy { it.publishedAt }
        val latest = items.last()
        val color = items.asReversed().firstNotNullOfOrNull { parseHexColor(it.botColor) } ?: fallbackColor(id)
        val cursor = readCursors[id] ?: 0L
        BotButton(
            id = id,
            name = latest.botName.ifBlank { fallbackName(id) },
            colorRgb = color,
            hasUnread = items.any { it.publishedAt > cursor },
        )
    }
}

fun mergeKnownBots(existing: List<KnownBot>, messages: List<ChatMessage>): List<KnownBot> {
    val merged = existing.filter { it.id.isNotBlank() }.associateBy { it.id }.toMutableMap()
    messages.sortedBy { it.publishedAt }.forEach { message ->
        if (message.botId.isBlank()) return@forEach
        val previous = merged[message.botId]
        val name = message.botName.ifBlank { previous?.name?.takeIf { it.isNotBlank() } ?: fallbackName(message.botId) }
        val color = parseHexColor(message.botColor)?.let { rgbToHex(it) }
            ?: previous?.colorHex?.takeIf { it.isNotBlank() }
            ?: rgbToHex(fallbackColor(message.botId))
        merged[message.botId] = KnownBot(message.botId, name, color)
    }
    return merged.values.sortedBy { it.id }
}

fun orderedCatalog(settings: AppSettings): List<KnownBot> {
    val fromMessages = settings.knownBots.associateBy { it.id }
    val defaults = DEFAULT_BOT_ORDER.associateWith { id ->
        KnownBot(id, fallbackName(id), rgbToHex(fallbackColor(id)))
    }
    val all = defaults + fromMessages
    val ids = (settings.botOrder + all.keys).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return ids.map { id ->
        all[id] ?: KnownBot(id, fallbackName(id), rgbToHex(fallbackColor(id)))
    }
}

data class PageGroup(
    val botId: String,
    val botName: String,
    val colorRgb: Int,
    val pages: List<PageEntry>,
)

fun groupPages(
    pages: List<PageEntry>,
    hidden: Set<String>,
    showHidden: Boolean,
    order: List<String>,
): List<PageGroup> {
    val visible = pages.filter { page ->
        page.canvasId.isNotBlank() && (showHidden || page.canvasId !in hidden)
    }
    val rank = order.withIndex().associate { it.value to it.index }
    return visible.groupBy { it.botId.ifBlank { "unknown" } }
        .toList()
        .sortedWith(
            compareBy<Pair<String, List<PageEntry>>> { rank[it.first] ?: Int.MAX_VALUE }
                .thenBy { it.second.firstOrNull()?.botName.orEmpty() }
                .thenBy { it.first },
        )
        .map { (botId, items) ->
            val name = items.firstNotNullOfOrNull { it.botName.takeIf(String::isNotBlank) }
                ?: if (botId == "unknown") "לא ידוע" else fallbackName(botId)
            val color = items.firstNotNullOfOrNull { parseHexColor(fromMessagesColor(botId)) } ?: fallbackColor(botId)
            PageGroup(
                botId = botId,
                botName = name,
                colorRgb = color,
                pages = items.sortedWith(compareBy({ it.title }, { it.canvasId })),
            )
        }
}

private fun fromMessagesColor(botId: String): String? = fallbackColors[botId]?.let { rgbToHex(it) }
