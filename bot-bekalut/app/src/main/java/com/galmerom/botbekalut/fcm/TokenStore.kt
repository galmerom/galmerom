package com.galmerom.botbekalut.fcm

import android.content.Context

internal object TokenStore {
    private const val PREFS = "bot_bekalut_token"
    private const val KEY = "token"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, token).apply()
    }

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }
}
