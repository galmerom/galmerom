package com.galmerom.outfittracker.data

import android.content.Context
import com.galmerom.outfittracker.R

object FirebaseConfig {
    fun isConfigured(context: Context): Boolean {
        val appId = resourceString(context, "google_app_id") ?: return false
        return appId.isNotBlank()
    }

    fun webClientId(context: Context): String? {
        val override = context.getString(R.string.web_client_id).trim()
        if (override.isNotEmpty() && !override.startsWith("YOUR_")) return override
        val generated = resourceString(context, "default_web_client_id")?.trim().orEmpty()
        return generated.takeIf { it.isNotEmpty() && !it.startsWith("YOUR_") }
    }

    private fun resourceString(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) return null
        return runCatching { context.getString(id) }.getOrNull()
    }
}
