package com.galmerom.botbekalut.data

import android.content.Context
import com.galmerom.botbekalut.domain.ChatMessage
import com.galmerom.botbekalut.domain.PageEntry
import com.galmerom.botbekalut.domain.prepareMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CacheFile(
    val savedAt: Long = 0L,
    val messages: List<ChatMessage> = emptyList(),
    val pages: List<PageEntry> = emptyList(),
    val hiddenPageIds: List<String> = emptyList(),
)

class MessageCache(context: Context) {
    private val file = File(context.filesDir, "bot-bekalut-cache.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(now: Long): CacheFile = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext CacheFile()
        val decoded = runCatching { json.decodeFromString<CacheFile>(file.readText()) }.getOrDefault(CacheFile())
        decoded.copy(messages = prepareMessages(decoded.messages, now))
    }

    suspend fun save(
        messages: List<ChatMessage>,
        pages: List<PageEntry>,
        hiddenPageIds: Set<String>,
        now: Long,
    ) = withContext(Dispatchers.IO) {
        val payload = CacheFile(
            savedAt = now,
            messages = prepareMessages(messages, now),
            pages = pages,
            hiddenPageIds = hiddenPageIds.toList(),
        )
        file.writeText(json.encodeToString(payload))
    }

    suspend fun saveHidden(hiddenPageIds: Set<String>, now: Long) = withContext(Dispatchers.IO) {
        val current = if (file.exists()) {
            runCatching { json.decodeFromString<CacheFile>(file.readText()) }.getOrDefault(CacheFile())
        } else {
            CacheFile()
        }
        file.writeText(json.encodeToString(current.copy(hiddenPageIds = hiddenPageIds.toList(), savedAt = now)))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
    }
}
