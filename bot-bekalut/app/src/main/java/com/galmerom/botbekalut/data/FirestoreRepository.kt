package com.galmerom.botbekalut.data

import com.galmerom.botbekalut.domain.AppSettings
import com.galmerom.botbekalut.domain.ChatImage
import com.galmerom.botbekalut.domain.ChatMessage
import com.galmerom.botbekalut.domain.DISPLAY_WINDOW_MS
import com.galmerom.botbekalut.domain.PageEntry
import com.galmerom.botbekalut.domain.prepareMessages
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Date

sealed interface RefreshOutcome {
    data class Success(
        val messages: List<ChatMessage>,
        val pages: List<PageEntry>,
        val hiddenPageIds: Set<String>,
    ) : RefreshOutcome

    data object Denied : RefreshOutcome
    data object Failed : RefreshOutcome
}

class FirestoreRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun refresh(uid: String, now: Long): RefreshOutcome {
        return try {
            val cutoff = Timestamp(Date(now - DISPLAY_WINDOW_MS))
            val messageSnap = db.collection("messages")
                .whereGreaterThan("publishedAt", cutoff)
                .orderBy("publishedAt", Query.Direction.ASCENDING)
                .get()
                .await()
            val pageSnap = db.collection("pages").get().await()
            val hiddenSnap = db.collection("users").document(uid).collection("hiddenPages").get().await()
            RefreshOutcome.Success(
                messages = prepareMessages(messageSnap.documents.mapNotNull { it.toMessage() }, now),
                pages = pageSnap.documents.mapNotNull { it.toPage() },
                hiddenPageIds = hiddenSnap.documents.map { it.id }.toSet(),
            )
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                RefreshOutcome.Denied
            } else {
                RefreshOutcome.Failed
            }
        } catch (_: Exception) {
            RefreshOutcome.Failed
        }
    }

    suspend fun loadSettings(uid: String): AppSettings? {
        return try {
            val snap = db.collection("users").document(uid).collection("settings").document("app").get().await()
            val raw = snap.getString("json") ?: return null
            json.decodeFromString<AppSettings>(raw).sanitized()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveSettings(uid: String, settings: AppSettings) {
        db.collection("users").document(uid).collection("settings").document("app")
            .set(
                mapOf(
                    "json" to json.encodeToString(settings),
                    "updatedAt" to settings.updatedAt,
                ),
            )
            .await()
    }

    suspend fun saveToken(uid: String, token: String) {
        db.collection("users").document(uid).collection("fcmTokens").document(tokenDocId(token))
            .set(
                mapOf(
                    "token" to token,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "platform" to "android",
                ),
            )
            .await()
    }

    suspend fun deleteToken(uid: String, token: String) {
        db.collection("users").document(uid).collection("fcmTokens").document(tokenDocId(token)).delete().await()
    }

    suspend fun hidePage(uid: String, canvasId: String) {
        db.collection("users").document(uid).collection("hiddenPages").document(canvasId)
            .set(
                mapOf(
                    "canvasId" to canvasId,
                    "hiddenAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    suspend fun unhidePage(uid: String, canvasId: String) {
        db.collection("users").document(uid).collection("hiddenPages").document(canvasId).delete().await()
    }
}

internal fun tokenDocId(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(40)
}

private fun DocumentSnapshot.toMessage(): ChatMessage? {
    val publishedAt = getTimestamp("publishedAt")?.toDate()?.time ?: return null
    val botId = getString("botId").orEmpty()
    if (botId.isBlank()) return null
    return ChatMessage(
        id = id,
        botId = botId,
        botName = getString("botName").orEmpty(),
        botColor = getString("botColor"),
        routineId = getString("routineId").orEmpty(),
        routineName = getString("routineName").orEmpty(),
        publishedAt = publishedAt,
        content = getString("content").orEmpty(),
        images = readImages(get("images")),
        sourceMessageId = getString("sourceMessageId"),
    )
}

private fun readImages(raw: Any?): List<ChatImage> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val url = map["url"] as? String ?: return@mapNotNull null
        if (url.isBlank()) return@mapNotNull null
        ChatImage(url = url, alt = map["alt"] as? String ?: "")
    }
}

private fun DocumentSnapshot.toPage(): PageEntry? {
    val canvasId = getString("canvasId")?.takeIf { it.isNotBlank() } ?: id
    if (canvasId.isBlank()) return null
    return PageEntry(
        canvasId = canvasId,
        title = getString("title").orEmpty(),
        description = getString("description").orEmpty(),
        url = getString("url").orEmpty(),
        botId = getString("botId").orEmpty().ifBlank { "unknown" },
        botName = getString("botName").orEmpty(),
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
    )
}
