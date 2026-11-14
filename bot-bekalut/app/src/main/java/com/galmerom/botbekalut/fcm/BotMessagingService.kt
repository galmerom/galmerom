package com.galmerom.botbekalut.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BotMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) = storeToken(token)

    override fun onRegistered(token: String) = storeToken(token)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] == "page") return
        val botId = data["botId"].orEmpty()
        val messageId = data["messageId"].orEmpty()
        val title = message.notification?.title ?: data["title"].orEmpty()
        val body = message.notification?.body ?: data["body"] ?: data["preview"].orEmpty()
        if (botId.isBlank() && title.isBlank() && body.isBlank()) return
        NotificationCenter.notifyChat(
            context = this,
            botId = botId,
            botName = title.ifBlank { botId },
            body = body,
            messageId = messageId,
        )
        ChatEvents.refreshes.tryEmit(Unit)
    }

    private fun storeToken(token: String) {
        if (token.isBlank()) return
        TokenStore.save(this, token)
        ChatEvents.tokens.tryEmit(token)
    }
}
