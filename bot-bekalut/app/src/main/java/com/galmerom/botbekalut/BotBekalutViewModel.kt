package com.galmerom.botbekalut

import android.app.Application
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.botbekalut.data.FirestoreRepository
import com.galmerom.botbekalut.data.MessageCache
import com.galmerom.botbekalut.data.RefreshOutcome
import com.galmerom.botbekalut.data.SettingsStore
import com.galmerom.botbekalut.domain.ALLOWED_EMAIL
import com.galmerom.botbekalut.domain.AppSettings
import com.galmerom.botbekalut.domain.ChatMessage
import com.galmerom.botbekalut.domain.PageEntry
import com.galmerom.botbekalut.domain.ScrollAnchor
import com.galmerom.botbekalut.domain.WEB_CLIENT_ID
import com.galmerom.botbekalut.domain.mergeKnownBots
import com.galmerom.botbekalut.domain.orderedCatalog
import com.galmerom.botbekalut.domain.topicFor
import com.galmerom.botbekalut.domain.visibleBots
import com.galmerom.botbekalut.domain.visibleMessages
import com.galmerom.botbekalut.fcm.ChatEvents
import com.galmerom.botbekalut.fcm.NotificationCenter
import com.galmerom.botbekalut.fcm.TokenStore
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class ScrollCommand(
    val botId: String,
    val messageId: String?,
    val highlight: Boolean,
    val offset: Int,
    val nonce: Long,
)

sealed interface Gate {
    data object Loading : Gate
    data object SignedOut : Gate
    data class Denied(val email: String?) : Gate
    data class Ready(val uid: String, val email: String) : Gate
}

data class UiState(
    val gate: Gate = Gate.Loading,
    val messages: List<ChatMessage> = emptyList(),
    val pages: List<PageEntry> = emptyList(),
    val hiddenPageIds: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val refreshing: Boolean = false,
    val signingIn: Boolean = false,
    val authError: String? = null,
    val activeBotId: String? = null,
)

class BotBekalutViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val cache = MessageCache(app)
    private val repo = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()
    private val settingsMutex = Mutex()
    private val settingsReady = CompletableDeferred<Unit>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts = _toasts.asSharedFlow()

    private val _scroll = MutableStateFlow<ScrollCommand?>(null)
    val scroll: StateFlow<ScrollCommand?> = _scroll.asStateFlow()

    private var activeUid: String? = null
    private var appliedTopics: Set<String>? = null
    private val positioned = mutableSetOf<String>()
    private var remoteWrite: Job? = null
    private var consumedLaunch: String? = null
    private var pendingLaunch: PendingLaunch? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        viewModelScope.launch { onAuth(firebaseAuth.currentUser) }
    }

    init {
        viewModelScope.launch {
            var first = true
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                if (first) {
                    first = false
                    settingsReady.complete(Unit)
                }
                syncTopics(settings)
            }
        }
        viewModelScope.launch {
            ChatEvents.refreshes.collect { refresh(userInitiated = false) }
        }
        viewModelScope.launch {
            ChatEvents.tokens.collect { token ->
                val uid = (_state.value.gate as? Gate.Ready)?.uid ?: return@collect
                runCatching { repo.saveToken(uid, token) }
            }
        }
        auth.addAuthStateListener(authListener)
    }

    fun signIn(context: Context) {
        if (_state.value.signingIn) return
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, authError = null) }
            try {
                val credentialManager = CredentialManager.create(context)
                val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (
                    credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    _state.update { it.copy(signingIn = false, authError = "הכניסה נכשלה. נסה שוב.") }
                    return@launch
                }
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(firebaseCredential).await()
                _state.update { it.copy(signingIn = false, authError = null) }
            } catch (_: GetCredentialCancellationException) {
                _state.update { it.copy(signingIn = false, authError = "הכניסה בוטלה.") }
            } catch (_: Exception) {
                _state.update { it.copy(signingIn = false, authError = "הכניסה נכשלה. נסה שוב.") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val gate = _state.value.gate as? Gate.Ready
            val token = TokenStore.read(getApplication())
            if (gate != null && token != null) {
                runCatching { repo.deleteToken(gate.uid, token) }
            }
            appliedTopics.orEmpty().forEach { topic ->
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            }
            appliedTopics = null
            NotificationCenter.clearAll(getApplication())
            cache.clear()
            runCatching {
                CredentialManager.create(getApplication()).clearCredentialState(ClearCredentialStateRequest())
            }
            activeUid = null
            positioned.clear()
            pendingLaunch = null
            auth.signOut()
            _state.update {
                it.copy(
                    gate = Gate.SignedOut,
                    messages = emptyList(),
                    pages = emptyList(),
                    hiddenPageIds = emptySet(),
                    refreshing = false,
                    signingIn = false,
                    activeBotId = null,
                )
            }
        }
    }

    fun refresh(userInitiated: Boolean) {
        val gate = _state.value.gate as? Gate.Ready ?: return
        if (_state.value.refreshing && !userInitiated) return
        viewModelScope.launch { refreshNow(gate.uid, userInitiated) }
    }

    fun onBotSettled(botId: String) {
        if (botId.isBlank()) return
        if (_state.value.activeBotId != botId) {
            _state.update { it.copy(activeBotId = botId) }
        }
        if (_state.value.settings.lastBotId != botId) {
            updateSettings { it.copy(lastBotId = botId) }
        }
        if (botId !in positioned && messagesFor(botId).isNotEmpty()) {
            positioned += botId
            emitScroll(botId)
        }
    }

    fun revealUnread(botId: String) {
        if (messagesFor(botId).isEmpty()) return
        emitScroll(botId)
    }

    fun consumeLaunch(botId: String?, openUnread: Boolean, issuedAt: Long) {
        val key = "${botId.orEmpty()}|$openUnread|$issuedAt"
        if (key == consumedLaunch) return
        consumedLaunch = key
        if (botId.isNullOrBlank() && !openUnread) return
        pendingLaunch = PendingLaunch(botId, openUnread)
        applyPendingLaunch()
    }

    fun saveAnchor(botId: String, messageId: String, offset: Int) {
        val current = _state.value.settings.scrollAnchors[botId]
        if (current?.messageId == messageId && current.offset == offset) return
        updateSettings { it.copy(scrollAnchors = it.scrollAnchors + (botId to ScrollAnchor(messageId, offset))) }
    }

    fun markReadToEnd(botId: String) {
        val latest = messagesFor(botId).maxOfOrNull { it.publishedAt } ?: return
        val current = _state.value.settings.readCursors[botId] ?: 0L
        if (latest <= current) return
        updateSettings { it.copy(readCursors = it.readCursors + (botId to latest)) }
        NotificationCenter.clearBot(getApplication(), botId)
    }

    fun clearFeed(botId: String) {
        val latest = _state.value.messages.filter { it.botId == botId }.maxOfOrNull { it.publishedAt } ?: return
        updateSettings {
            it.copy(
                clearedBefore = it.clearedBefore + (botId to maxOf(latest, it.clearedBefore[botId] ?: 0L)),
                readCursors = it.readCursors + (botId to maxOf(latest, it.readCursors[botId] ?: 0L)),
            )
        }
        NotificationCenter.clearBot(getApplication(), botId)
    }

    fun setTheme(themeId: String) = updateSettings { it.copy(themeId = themeId) }

    fun setFont(fontId: String) = updateSettings { it.copy(fontScale = fontId) }

    fun setMuted(botId: String, muted: Boolean) {
        updateSettings { settings ->
            val next = if (muted) settings.mutedBotIds + botId else settings.mutedBotIds - botId
            settings.copy(mutedBotIds = next.distinct())
        }
    }

    fun moveBot(botId: String, delta: Int) {
        val ids = orderedCatalog(_state.value.settings).map { it.id }.toMutableList()
        val index = ids.indexOf(botId)
        val target = index + delta
        if (index < 0 || target !in ids.indices) return
        val item = ids.removeAt(index)
        ids.add(target, item)
        updateSettings { it.copy(botOrder = ids) }
    }

    fun setPageHidden(canvasId: String, hidden: Boolean) {
        val gate = _state.value.gate as? Gate.Ready ?: return
        val before = _state.value.hiddenPageIds
        val after = if (hidden) before + canvasId else before - canvasId
        _state.update { it.copy(hiddenPageIds = after) }
        viewModelScope.launch {
            val ok = runCatching {
                if (hidden) repo.hidePage(gate.uid, canvasId) else repo.unhidePage(gate.uid, canvasId)
            }.isSuccess
            if (!ok) {
                _state.update { it.copy(hiddenPageIds = before) }
                _toasts.emit("לא הצלחתי לעדכן")
            } else {
                cache.saveHidden(after, System.currentTimeMillis())
            }
        }
    }

    fun markNotificationAsked() = updateSettings { it.copy(askedNotificationPermission = true) }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
    }

    private suspend fun onAuth(user: FirebaseUser?) {
        settingsReady.await()
        if (user == null) {
            if (_state.value.gate !is Gate.SignedOut) {
                activeUid = null
                _state.update { it.copy(gate = Gate.SignedOut, refreshing = false, signingIn = false) }
            }
            return
        }
        if (user.uid == activeUid && _state.value.gate is Gate.Ready) return
        runCatching { user.reload().await() }
        val fresh = auth.currentUser ?: user
        val email = fresh.email
        if (!email.equals(ALLOWED_EMAIL, ignoreCase = true) || !fresh.isEmailVerified) {
            activeUid = null
            _state.update { it.copy(gate = Gate.Denied(email), signingIn = false, refreshing = false) }
            return
        }
        activeUid = fresh.uid
        val now = System.currentTimeMillis()
        val cached = cache.load(now)
        val settings = _state.value.settings
        val active = pickBot(cached.messages, settings)
        _state.update {
            it.copy(
                gate = Gate.Ready(fresh.uid, email ?: ALLOWED_EMAIL),
                messages = cached.messages,
                pages = cached.pages,
                hiddenPageIds = cached.hiddenPageIds.toSet(),
                signingIn = false,
                authError = null,
                activeBotId = active,
            )
        }
        rememberBots(cached.messages)
        syncTopics(_state.value.settings)
        uploadToken(fresh.uid)
        pullRemoteSettings(fresh.uid)
        positionIfNeeded()
        refreshNow(fresh.uid, userInitiated = false)
    }

    private suspend fun refreshNow(uid: String, userInitiated: Boolean) {
        val before = _state.value.messages.map { it.id }.toSet()
        _state.update { it.copy(refreshing = true) }
        val now = System.currentTimeMillis()
        when (val outcome = repo.refresh(uid, now)) {
            RefreshOutcome.Denied -> {
                _state.update { it.copy(gate = Gate.Denied((it.gate as? Gate.Ready)?.email), refreshing = false) }
            }
            RefreshOutcome.Failed -> {
                _state.update { it.copy(refreshing = false) }
                if (userInitiated) _toasts.emit("לא הצלחתי לרענן")
            }
            is RefreshOutcome.Success -> {
                cache.save(outcome.messages, outcome.pages, outcome.hiddenPageIds, now)
                val added = outcome.messages.map { it.id }.toSet() - before
                _state.update {
                    it.copy(
                        refreshing = false,
                        messages = outcome.messages,
                        pages = outcome.pages,
                        hiddenPageIds = outcome.hiddenPageIds,
                        activeBotId = it.activeBotId ?: pickBot(outcome.messages, it.settings),
                    )
                }
                rememberBots(outcome.messages)
                syncTopics(_state.value.settings)
                if (userInitiated && added.isEmpty()) _toasts.emit("אין עדכונים חדשים")
                positionIfNeeded()
                applyPendingLaunch()
            }
        }
    }

    private fun rememberBots(messages: List<ChatMessage>) {
        val merged = mergeKnownBots(_state.value.settings.knownBots, messages)
        if (merged != _state.value.settings.knownBots) {
            updateSettings { it.copy(knownBots = merged) }
        }
    }

    private suspend fun pullRemoteSettings(uid: String) {
        val remote = repo.loadSettings(uid) ?: return
        if (remote.updatedAt > _state.value.settings.updatedAt) {
            settingsMutex.withLock { store.update { remote.sanitized() } }
        }
    }

    @Suppress("DEPRECATION")
    private fun uploadToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            TokenStore.save(getApplication(), token)
            viewModelScope.launch { runCatching { repo.saveToken(uid, token) } }
        }
    }

    private fun positionIfNeeded() {
        val buttons = currentButtons()
        if (buttons.isEmpty()) return
        val preferred = _state.value.activeBotId ?: _state.value.settings.lastBotId
        val target = buttons.firstOrNull { it.id == preferred }?.id ?: buttons.first().id
        if (_state.value.activeBotId != target) {
            _state.update { it.copy(activeBotId = target) }
        }
        if (messagesFor(target).isEmpty()) return
        if (target !in positioned) {
            positioned += target
            emitScroll(target)
        }
        applyPendingLaunch()
    }

    private fun applyPendingLaunch() {
        val pending = pendingLaunch ?: return
        val buttons = currentButtons()
        if (buttons.isEmpty()) return
        val resolved = when {
            !pending.botId.isNullOrBlank() && buttons.any { it.id == pending.botId } -> pending.botId
            pending.openUnread -> buttons.firstOrNull { it.hasUnread }?.id ?: buttons.first().id
            else -> buttons.firstOrNull { it.hasUnread }?.id ?: buttons.first().id
        }
        pendingLaunch = null
        _state.update { it.copy(activeBotId = resolved) }
        updateSettings { it.copy(lastBotId = resolved) }
        positioned += resolved
        emitScroll(resolved)
    }

    private fun emitScroll(botId: String) {
        val messages = messagesFor(botId)
        if (messages.isEmpty()) return
        val cursor = _state.value.settings.readCursors[botId] ?: 0L
        val firstUnread = messages.firstOrNull { it.publishedAt > cursor }
        val anchor = _state.value.settings.scrollAnchors[botId]
        _scroll.value = when {
            firstUnread != null -> ScrollCommand(botId, firstUnread.id, highlight = true, offset = 0, nonce = System.nanoTime())
            !anchor?.messageId.isNullOrBlank() -> ScrollCommand(
                botId,
                anchor.messageId,
                highlight = false,
                offset = anchor.offset,
                nonce = System.nanoTime(),
            )
            else -> ScrollCommand(botId, messageId = null, highlight = false, offset = 0, nonce = System.nanoTime())
        }
    }

    private fun pickBot(messages: List<ChatMessage>, settings: AppSettings): String? {
        val buttons = visibleBots(
            messages = messages,
            order = settings.botOrder,
            readCursors = settings.readCursors,
            now = System.currentTimeMillis(),
        )
        return buttons.firstOrNull { it.id == settings.lastBotId }?.id ?: buttons.firstOrNull()?.id
    }

    private fun currentButtons() = visibleBots(
        messages = _state.value.messages,
        order = _state.value.settings.botOrder,
        readCursors = _state.value.settings.readCursors,
        now = System.currentTimeMillis(),
    )

    private fun messagesFor(botId: String) = visibleMessages(_state.value.messages, _state.value.settings.clearedBefore).filter { it.botId == botId }.sortedBy { it.publishedAt }

    private fun syncTopics(settings: AppSettings) {
        if (_state.value.gate !is Gate.Ready) return
        val known = (settings.botOrder + settings.knownBots.map { it.id }).toSet()
        val desired = known.mapNotNull { id ->
            if (id in settings.mutedBotIds) null else topicFor(id)
        }.toSet()
        if (desired == appliedTopics) return
        val messaging = FirebaseMessaging.getInstance()
        val previous = appliedTopics
        val removed = previous.orEmpty() - desired
        val added = if (previous == null) desired else desired - previous
        added.forEach { messaging.subscribeToTopic(it) }
        removed.forEach { messaging.unsubscribeFromTopic(it) }
        appliedTopics = desired
    }

    private fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsMutex.withLock {
                store.update { current -> block(current).copy(updatedAt = System.currentTimeMillis()).sanitized() }
            }
            scheduleRemoteWrite()
        }
    }

    private fun scheduleRemoteWrite() {
        val uid = (_state.value.gate as? Gate.Ready)?.uid ?: return
        remoteWrite?.cancel()
        remoteWrite = viewModelScope.launch {
            delay(1_500)
            val latest = _state.value.settings
            runCatching { repo.saveSettings(uid, latest) }
        }
    }

    private data class PendingLaunch(val botId: String?, val openUnread: Boolean)
}
