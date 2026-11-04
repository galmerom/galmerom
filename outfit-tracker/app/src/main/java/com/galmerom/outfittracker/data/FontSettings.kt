package com.galmerom.outfittracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.galmerom.outfittracker.data.awaitCommitted
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

enum class FontSize(val key: String, val scale: Float) {
    REGULAR("regular", 1f),
    LARGE("large", 1.15f),
    EXTRA_LARGE("extra_large", 1.35f),
    ;

    companion object {
        fun fromKey(value: String?): FontSize = entries.firstOrNull { it.key == value } ?: REGULAR
    }
}

enum class AppTheme(val key: String, val dark: Boolean) {
    WARM("warm", false),
    OCEAN("ocean", false),
    SAGE("sage", false),
    LILAC("lilac", false),
    NIGHT("night", true),
    ;

    companion object {
        fun fromKey(value: String?): AppTheme = entries.firstOrNull { it.key == value } ?: WARM
    }
}

data class UserPrefs(
    val fontSize: FontSize = FontSize.REGULAR,
    val theme: AppTheme = AppTheme.WARM,
)

private val Context.settingsDataStore by preferencesDataStore(name = "outfit_tracker_settings")

class UserPreferences(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val fontKey = stringPreferencesKey("font_size")
    private val themeKey = stringPreferencesKey("theme")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cached = MutableStateFlow(readCached())
    private var accountSyncStarted = false
    private var localRevision = 0
    private var ackedRevision = 0

    val prefs: StateFlow<UserPrefs> = cached

    fun setFontSize(size: FontSize) {
        localRevision++
        update(cached.value.copy(fontSize = size))
    }

    fun setTheme(theme: AppTheme) {
        localRevision++
        update(cached.value.copy(theme = theme))
    }

    fun startAccountSync(firestore: FirebaseFirestore, auth: FirebaseAuth) {
        if (accountSyncStarted) return
        val uid = auth.currentUser?.uid ?: return
        accountSyncStarted = true
        val doc = firestore.collection("users").document(uid).collection("settings").document("preferences")
        scope.launch {
            val snapshot = runCatching { doc.get().await() }.getOrNull() ?: return@launch
            if (!snapshot.exists()) {
                push(doc)
                return@launch
            }
            applyRemote(snapshot.getString("fontSize"), snapshot.getString("theme"))
        }
        doc.addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            applyRemote(snapshot.getString("fontSize"), snapshot.getString("theme"))
        }
    }

    fun pushToAccount(firestore: FirebaseFirestore, auth: FirebaseAuth) {
        val uid = auth.currentUser?.uid ?: return
        val doc = firestore.collection("users").document(uid).collection("settings").document("preferences")
        scope.launch { push(doc) }
    }

    private fun update(next: UserPrefs) {
        cached.value = next
        scope.launch {
            store.edit { prefs ->
                prefs[fontKey] = next.fontSize.key
                prefs[themeKey] = next.theme.key
            }
        }
    }

    private fun applyRemote(font: String?, theme: String?) {
        if (localRevision != ackedRevision) return
        val next = UserPrefs(FontSize.fromKey(font), AppTheme.fromKey(theme))
        if (next != cached.value) update(next)
    }

    private suspend fun push(doc: com.google.firebase.firestore.DocumentReference) {
        val revision = localRevision
        val current = cached.value
        doc.set(
            mapOf(
                "fontSize" to current.fontSize.key,
                "theme" to current.theme.key,
            ),
            SetOptions.merge(),
        ).awaitCommitted().onSuccess {
            if (revision == localRevision) ackedRevision = revision
        }
    }

    private fun readCached(): UserPrefs = runBlocking {
        withTimeoutOrNull(400) {
            val prefs = store.data.first()
            UserPrefs(
                fontSize = FontSize.fromKey(prefs[fontKey]),
                theme = AppTheme.fromKey(prefs[themeKey]),
            )
        } ?: UserPrefs()
    }
}
