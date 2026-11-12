package com.galmerom.botbekalut.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChatImage(
    val url: String = "",
    val alt: String = "",
)

@Serializable
data class ChatMessage(
    val id: String = "",
    val botId: String = "",
    val botName: String = "",
    val botColor: String? = null,
    val routineId: String = "",
    val routineName: String = "",
    val publishedAt: Long = 0L,
    val content: String = "",
    val images: List<ChatImage> = emptyList(),
    val sourceMessageId: String? = null,
)

@Serializable
data class PageEntry(
    val canvasId: String = "",
    val title: String = "",
    val description: String = "",
    val url: String = "",
    val botId: String = "",
    val botName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

const val DISPLAY_WINDOW_MS = 24L * 60 * 60 * 1000

fun inDisplayWindow(publishedAt: Long, now: Long): Boolean = publishedAt > now - DISPLAY_WINDOW_MS

fun prepareMessages(messages: List<ChatMessage>, now: Long): List<ChatMessage> {
    val kept = LinkedHashMap<String, ChatMessage>()
    messages.sortedBy { it.publishedAt }.forEach { message ->
        if (!inDisplayWindow(message.publishedAt, now)) return@forEach
        if (message.id.isBlank() || message.botId.isBlank()) return@forEach
        val key = message.sourceMessageId?.takeIf { it.isNotBlank() }?.let { "src:$it" } ?: "id:${message.id}"
        kept[key] = message
    }
    return kept.values.sortedBy { it.publishedAt }
}

private val BROADCAST_CLOCK_LINE = Regex("^\\s*\uD83D\uDD50\\uFE0F?\\s*\\d{1,2}:\\d{2}\\s*$")

private fun isRuleLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.isNotEmpty() && trimmed.all { it == '\u2501' }
}

/**
 * Removes the temporary header some bots put before content: lines made only of the heavy
 * horizontal rule character, then an optional clock line like "\uD83D\uDD50 21:05".
 * The app draws its own divider and broadcast time instead.
 */
fun stripBroadcastHeader(content: String): String {
    val lines = content.split("\n")
    var index = 0
    while (index < lines.size && lines[index].isBlank()) index++
    if (index >= lines.size || !isRuleLine(lines[index])) return content
    while (index < lines.size && isRuleLine(lines[index])) index++
    var next = index
    while (next < lines.size && lines[next].isBlank()) next++
    if (next < lines.size && BROADCAST_CLOCK_LINE.matches(lines[next])) index = next + 1
    return lines.drop(index).dropWhile { it.isBlank() }.joinToString("\n")
}

/** Hides messages the user cleared on this phone (publishedAt at or before the per-bot timestamp) and strips the bot header. */
fun visibleMessages(messages: List<ChatMessage>, clearedBefore: Map<String, Long>): List<ChatMessage> =
    messages
        .filter { it.publishedAt > (clearedBefore[it.botId] ?: 0L) }
        .map { message ->
            val stripped = stripBroadcastHeader(message.content)
            if (stripped == message.content) message else message.copy(content = stripped)
        }
