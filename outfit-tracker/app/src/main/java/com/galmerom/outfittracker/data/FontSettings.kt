package com.galmerom.outfittracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class FontSize(val key: String, val scale: Float) {
    REGULAR("regular", 1f),
    LARGE("large", 1.15f),
    EXTRA_LARGE("extra_large", 1.35f),
    ;

    companion object {
        fun fromKey(value: String?): FontSize = entries.firstOrNull { it.key == value } ?: REGULAR
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "outfit_tracker_settings")

class FontSettings(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val key = stringPreferencesKey("font_size")

    val choice: Flow<FontSize> = store.data.map { prefs -> FontSize.fromKey(prefs[key]) }

    suspend fun set(size: FontSize) {
        store.edit { prefs -> prefs[key] = size.key }
    }
}
