package com.galmerom.outfittracker.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckInstaller {
    private const val TAG = "OutfitTracker"

    fun install(context: Context) {
        val app = FirebaseApp.getInstance()
        val appCheck = FirebaseAppCheck.getInstance(app)
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        appCheck.getAppCheckToken(false)
            .addOnCompleteListener {
                val secret = currentSecret(context)
                if (!secret.isNullOrBlank()) {
                    Log.i(TAG, "App Check debug token: $secret")
                }
                if (!it.isSuccessful) {
                    Log.e(TAG, "App Check token request failed", it.exception)
                }
            }
    }

    fun currentSecret(context: Context): String? {
        val app = runCatching { FirebaseApp.getInstance() }.getOrNull() ?: return null
        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${app.persistenceKey}",
            Context.MODE_PRIVATE,
        )
        return prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
    }
}
