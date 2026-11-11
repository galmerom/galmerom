package com.galmerom.botbekalut.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.galmerom.botbekalut.MainActivity
import com.galmerom.botbekalut.R
import org.json.JSONObject
import java.security.MessageDigest

object NotificationCenter {
    const val CHANNEL_ID = "bot_bekalut_updates"
    private const val GROUP = "bot_bekalut_updates"
    private const val PREFS = "bot_bekalut_notification_inbox"
    private const val SUMMARY_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(CHANNEL_ID, "עדכוני בוטים", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "הודעות חדשות מהבוטים"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyChat(context: Context, botId: String, botName: String, body: String, messageId: String) {
        ensureChannel(context)
        val inbox = readInbox(context)
        val current = inbox[botId]
        if (messageId.isNotBlank() && current?.messageIds?.contains(messageId) == true) return
        val title = botName.ifBlank { current?.title ?: botId.ifBlank { "בוט בקלות" } }
        val preview = body.ifBlank { current?.body ?: "הודעה חדשה" }
        inbox[botId.ifBlank { "unknown" }] = BotNote(
            title = title,
            body = preview,
            messageIds = (current?.messageIds.orEmpty() + messageId).filter { it.isNotBlank() }.takeLast(30),
        )
        writeInbox(context, inbox)
        post(context, inbox)
    }

    fun clearBot(context: Context, botId: String) {
        val inbox = readInbox(context)
        if (inbox.remove(botId) == null) return
        writeInbox(context, inbox)
        NotificationManagerCompat.from(context).cancel(childId(botId))
        post(context, inbox)
    }

    fun clearAll(context: Context) {
        writeInbox(context, linkedMapOf())
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun post(context: Context, inbox: Map<String, BotNote>) {
        val manager = NotificationManagerCompat.from(context)
        if (inbox.isEmpty()) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val total = inbox.values.sumOf { it.messageIds.size.coerceAtLeast(1) }
        inbox.forEach { (botId, note) ->
            val count = note.messageIds.size.coerceAtLeast(1)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bot)
                .setColor(context.getColor(R.color.notification_color))
                .setContentTitle(note.title)
                .setContentText(note.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(note.body))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setGroup(GROUP)
                .setNumber(count)
                .setContentIntent(pending(context, childId(botId), botId, openUnread = false))
                .build()
            runCatching { manager.notify(childId(botId), notification) }
        }
        if (inbox.size >= 2) {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle("בוט בקלות")
            inbox.values.forEach { note ->
                style.addLine("${note.title}: ${note.body.take(80)}")
            }
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bot)
                .setColor(context.getColor(R.color.notification_color))
                .setContentTitle("בוט בקלות")
                .setContentText("$total עדכונים")
                .setStyle(style)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setNumber(total)
                .setAutoCancel(true)
                .setContentIntent(pending(context, SUMMARY_ID, botId = null, openUnread = true))
                .build()
            runCatching { manager.notify(SUMMARY_ID, summary) }
        } else {
            manager.cancel(SUMMARY_ID)
        }
    }

    private fun pending(context: Context, requestCode: Int, botId: String?, openUnread: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!botId.isNullOrBlank()) putExtra(MainActivity.EXTRA_BOT_ID, botId)
            putExtra(MainActivity.EXTRA_OPEN_UNREAD, openUnread)
            putExtra(MainActivity.EXTRA_ISSUED_AT, System.currentTimeMillis())
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun childId(botId: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(botId.toByteArray())
        val value = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
        return 2_000 + (value % 20_000)
    }

    private fun readInbox(context: Context): LinkedHashMap<String, BotNote> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("inbox", null) ?: return linkedMapOf()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return linkedMapOf()
        val out = linkedMapOf<String, BotNote>()
        json.keys().forEach { key ->
            val item = json.optJSONObject(key) ?: return@forEach
            val ids = item.optJSONArray("ids")
            val list = buildList {
                if (ids != null) {
                    for (index in 0 until ids.length()) {
                        val id = ids.optString(index)
                        if (id.isNotBlank()) add(id)
                    }
                }
            }
            out[key] = BotNote(item.optString("title"), item.optString("body"), list)
        }
        return out
    }

    private fun writeInbox(context: Context, inbox: Map<String, BotNote>) {
        val json = JSONObject()
        inbox.forEach { (key, note) ->
            json.put(
                key,
                JSONObject()
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("ids", org.json.JSONArray(note.messageIds)),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("inbox", json.toString()).apply()
    }

    private data class BotNote(
        val title: String,
        val body: String,
        val messageIds: List<String>,
    )
}
