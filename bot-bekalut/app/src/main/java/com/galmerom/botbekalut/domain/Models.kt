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
