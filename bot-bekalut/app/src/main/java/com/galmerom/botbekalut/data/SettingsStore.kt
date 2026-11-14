package com.galmerom.botbekalut.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.galmerom.botbekalut.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "bot_bekalut_settings")

class SettingsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val stateKey = stringPreferencesKey("state")

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .map { prefs -> decode(prefs[stateKey]) }
        .catch { emit(AppSettings()) }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = decode(prefs[stateKey])
            prefs[stateKey] = json.encodeToString(block(current).sanitized())
        }
    }

    private fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(raw).sanitized() }.getOrDefault(AppSettings())
    }
}
