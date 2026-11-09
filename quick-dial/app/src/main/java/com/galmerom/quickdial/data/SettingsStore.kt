package com.galmerom.quickdial.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.galmerom.quickdial.domain.QuickDialSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "quick_dial")

class SettingsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val stateKey = stringPreferencesKey("state")

    val settings: Flow<QuickDialSettings> = context.settingsDataStore.data
        .map { prefs -> decode(prefs[stateKey]) }
        .catch { emit(QuickDialSettings()) }

    suspend fun update(block: (QuickDialSettings) -> QuickDialSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = decode(prefs[stateKey])
            prefs[stateKey] = json.encodeToString(block(current).sanitized())
        }
    }

    private fun decode(raw: String?): QuickDialSettings {
        if (raw.isNullOrBlank()) return QuickDialSettings()
        return runCatching { json.decodeFromString<QuickDialSettings>(raw).sanitized() }
            .getOrDefault(QuickDialSettings())
    }
}
