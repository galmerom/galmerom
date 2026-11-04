package com.galmerom.outfittracker.data

import android.content.Context
import com.galmerom.outfittracker.R

object FirebaseConfig {
    fun isConfigured(context: Context): Boolean {
        val appId = resourceString(context, "google_app_id") ?: return false
        return appId.isNotBlank()
    }

    /**
     * Credential Manager needs the OAuth web client (client_type 3). The Google
     * Services plugin writes that id into `default_web_client_id` from google-services.json.
     * `web_client_id` is only an override when that generated value is missing.
     */
    fun webClientId(context: Context): String? {
        val generated = resourceString(context, "default_web_client_id")?.trim().orEmpty()
        if (generated.isNotEmpty() && !generated.startsWith("YOUR_")) return generated
        val override = context.getString(R.string.web_client_id).trim()
        return override.takeIf { it.isNotEmpty() && !it.startsWith("YOUR_") }
    }

    private fun resourceString(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) return null
        return runCatching { context.getString(id) }.getOrNull()
    }
}
