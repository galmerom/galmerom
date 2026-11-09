package com.galmerom.quickdial.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.quickdial.data.CallLogReader
import com.galmerom.quickdial.data.DeviceContact
import com.galmerom.quickdial.data.PhoneBook
import com.galmerom.quickdial.data.SettingsStore
import com.galmerom.quickdial.data.TilePhotos
import com.galmerom.quickdial.data.WhatsAppTarget
import com.galmerom.quickdial.data.phoneLabel
import com.galmerom.quickdial.domain.ContactNumbers
import com.galmerom.quickdial.domain.DialTheme
import com.galmerom.quickdial.domain.NumberOption
import com.galmerom.quickdial.domain.QuickDialSettings
import com.galmerom.quickdial.domain.SavedContact
import com.galmerom.quickdial.domain.ShortNameSize
import com.galmerom.quickdial.domain.Slide
import com.galmerom.quickdial.domain.TILE_RATIO_DEFAULT
import com.galmerom.quickdial.domain.tileRatio
import com.galmerom.quickdial.domain.cleanShortName
import com.galmerom.quickdial.domain.defaultShortName
import com.galmerom.quickdial.domain.distinctNumbers
import com.galmerom.quickdial.domain.matchesContactQuery
import com.galmerom.quickdial.domain.nameOrder
import com.galmerom.quickdial.domain.orderedIds
import com.galmerom.quickdial.domain.outgoingCallCounts
import com.galmerom.quickdial.domain.samePhone
import com.galmerom.quickdial.domain.sixMonthsBefore
import com.galmerom.quickdial.domain.suggestedIndex
import com.galmerom.quickdial.domain.slide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

enum class Screen { Home, Contacts, Reorder }

enum class NoticeKind {
    WhatsAppMissing,
    WhatsAppAppMissing,
    CallFailed,
    NoNumber,
    CallDenied,
    ContactsDenied,
    CameraDenied,
    PhotoFailed,
}

data class NoticeEvent(val kind: NoticeKind, val detail: String? = null)

data class Tile(
    val id: String,
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val shortName: String,
    val phoneNumber: String,
    val callCount: Int,
    val photoUri: String?,
)

sealed interface CallPhase {
    data object Idle : CallPhase
    data class NeedsPermission(val tile: Tile) : CallPhase
    data class Counting(val tile: Tile) : CallPhase
}

data class HomeUi(
    val tiles: List<Tile> = emptyList(),
    val columns: Int = 3,
    val tileRatio: Float = TILE_RATIO_DEFAULT,
    val frozen: Boolean = false,
    val showBadge: Boolean = false,
    val shortNameSp: Int = ShortNameSize.Medium.sp,
    val theme: DialTheme = DialTheme.Blue,
    val callPhase: CallPhase = CallPhase.Idle,
    val screen: Screen = Screen.Home,
    val contactsDenied: Boolean = false,
    val callLogDenied: Boolean = false,
    val ready: Boolean = false,
)

data class LabeledNumber(val number: String, val label: String, val suggested: Boolean)

data class PickerRow(
    val id: String,
    val displayName: String,
    val phones: List<LabeledNumber>,
    val selected: Boolean,
    val selectedNumber: String?,
    val shortName: String,
)

data class NumberPrompt(
    val rowId: String,
    val displayName: String,
    val options: List<LabeledNumber>,
    val selectedIndex: Int,
)

data class RenamePrompt(
    val rowId: String,
    val displayName: String,
    val initial: String,
    val hasCustomPhoto: Boolean,
)

data class PickerUi(
    val query: String = "",
    val rows: List<PickerRow> = emptyList(),
    val loading: Boolean = true,
    val numberPrompt: NumberPrompt? = null,
    val renamePrompt: RenamePrompt? = null,
)

class QuickDialViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val phoneBook = PhoneBook(app)
    private val callLog = CallLogReader(app)
    private val tilePhotos = TilePhotos(app)

    private var latestSettings = QuickDialSettings()
    private var latestDevices: List<DeviceContact> = emptyList()
    private var latestCounts: Map<String, Int> = emptyMap()
    private var photos: Map<String, String> = emptyMap()
    private var contactsGranted = false
    private var callLogGranted = false
    private var ready = false
    private var screen = Screen.Home
    private var callPhase: CallPhase = CallPhase.Idle
    private var query = ""
    private var numberPrompt: NumberPrompt? = null
    private var renamePrompt: RenamePrompt? = null
    private val pendingShort = mutableMapOf<String, String>()
    private val pendingPhoto = mutableMapOf<String, String>()
    private var refreshJob: Job? = null
    private var watching = false

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui
    private val _picker = MutableStateFlow(PickerUi())
    val picker: StateFlow<PickerUi> = _picker

    private val _notices = MutableSharedFlow<NoticeEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<NoticeEvent> = _notices.asSharedFlow()

    private val _whatsApp = MutableSharedFlow<WhatsAppTarget>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val whatsApp: SharedFlow<WhatsAppTarget> = _whatsApp.asSharedFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    init {
        viewModelScope.launch {
            store.settings.collect {
                latestSettings = it
                publish()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(80)
            val settings = store.settings.first()
            latestSettings = settings
            val contactsOk = hasPermission(Manifest.permission.READ_CONTACTS)
            val callLogOk = hasPermission(Manifest.permission.READ_CALL_LOG)
            val devices = withContext(Dispatchers.IO) {
                if (!contactsOk) emptyList()
                else runCatching { phoneBook.loadAll() }.getOrDefault(emptyList())
            }
            val paired = settings.contacts.map { it to findDevice(devices, it) }
            val renamed = paired.map { (contact, device) ->
                if (device == null) {
                    contact
                } else {
                    contact.copy(
                        contactId = device.contactId,
                        lookupKey = device.lookupKey.ifBlank { contact.lookupKey },
                        displayName = device.displayName.ifBlank { contact.displayName },
                    )
                }
            }
            if (renamed != settings.contacts) {
                store.update { current -> current.copy(contacts = renamed) }
                latestSettings = store.settings.first()
            }
            val now = System.currentTimeMillis()
            val since = sixMonthsBefore(now, ZoneId.systemDefault())
            val counts = withContext(Dispatchers.IO) {
                if (!callLogOk) {
                    emptyMap()
                } else {
                    val calls = runCatching { callLog.readOutgoingSince(since) }.getOrDefault(emptyList())
                    val owners = latestSettings.contacts.map { contact ->
                        val device = findDevice(devices, contact)
                        val numbers = device?.phones?.map { it.number }.orEmpty()
                            .ifEmpty { listOf(contact.phoneNumber) }
                        ContactNumbers(contact.id, numbers)
                    }
                    outgoingCallCounts(calls, owners, since, now)
                }
            }
            latestDevices = devices
            latestCounts = counts
            contactsGranted = contactsOk
            callLogGranted = callLogOk
            photos = latestSettings.contacts.mapNotNull { contact ->
                resolvePhoto(contact, findDevice(devices, contact))?.let { contact.id to it }
            }.toMap()
            ready = true
            watchCallLog()
            publish()
        }
    }

    fun openContacts() {
        screen = Screen.Contacts
        publish()
        refresh()
    }

    fun openReorder() {
        if (!latestSettings.frozen) return
        screen = Screen.Reorder
        publish()
    }

    fun goHome() {
        screen = Screen.Home
        query = ""
        numberPrompt = null
        renamePrompt = null
        publish()
    }

    fun setColumns(columns: Int) = updateSettings { it.copy(columns = columns) }

    fun setTileRatio(ratio: Float) = updateSettings { it.copy(tileRatio = tileRatio(ratio)) }

    fun resetTileRatio() = setTileRatio(TILE_RATIO_DEFAULT)

    fun setBadge(show: Boolean) = updateSettings { it.copy(showCallBadge = show) }

    fun setShortNameSize(size: ShortNameSize) = updateSettings { it.copy(shortNameSize = size.key) }

    fun setTheme(theme: DialTheme) = updateSettings { it.copy(theme = theme.key) }

    fun setFrozen(frozen: Boolean) {
        viewModelScope.launch {
            store.update { current ->
                if (frozen) {
                    val order = orderedIds(current.orderRequest(latestCounts).copy(frozen = false))
                    current.copy(frozen = true, frozenOrder = order)
                } else {
                    current.copy(frozen = false)
                }
            }
        }
    }

    fun slide(id: String, direction: Slide) {
        viewModelScope.launch {
            store.update { current ->
                if (!current.frozen) return@update current
                val order = orderedIds(current.orderRequest(latestCounts))
                current.copy(frozenOrder = slide(order, id, direction))
            }
        }
    }

    fun setQuery(value: String) {
        query = value
        publish()
    }

    fun toggle(rowId: String) {
        if (latestSettings.contacts.any { it.id == rowId }) {
            viewModelScope.launch {
                store.update { current ->
                    current.copy(
                        contacts = current.contacts.filterNot { it.id == rowId },
                        frozenOrder = current.frozenOrder.filterNot { it == rowId },
                    )
                }
                pendingShort.remove(rowId)
                refresh()
            }
            return
        }
        val device = latestDevices.find { it.id == rowId } ?: return
        val phones = labeledPhones(device)
        if (phones.isEmpty()) {
            notify(NoticeKind.NoNumber)
            return
        }
        if (phones.size == 1) {
            addContact(rowId, phones.first().number)
        } else {
            numberPrompt = NumberPrompt(
                rowId = rowId,
                displayName = device.displayName,
                options = phones,
                selectedIndex = phones.indexOfFirst { it.suggested }.coerceAtLeast(0),
            )
            publish()
        }
    }

    fun confirmNumber(index: Int) {
        val prompt = numberPrompt ?: return
        val number = prompt.options.getOrNull(index)?.number ?: return
        numberPrompt = null
        publish()
        addContact(prompt.rowId, number)
    }

    fun dismissNumber() {
        numberPrompt = null
        publish()
    }

    fun beginRename(rowId: String) {
        val device = latestDevices.find { it.id == rowId } ?: return
        val saved = latestSettings.contacts.find { it.id == rowId }
        val initial = saved?.shortName ?: pendingShort[rowId] ?: defaultShortName(device.displayName)
        val custom = saved?.customPhotoFile ?: pendingPhoto[rowId]
        renamePrompt = RenamePrompt(
            rowId = rowId,
            displayName = device.displayName,
            initial = initial,
            hasCustomPhoto = !custom.isNullOrBlank(),
        )
        publish()
    }

    fun saveRename(value: String) {
        val prompt = renamePrompt ?: return
        val cleaned = cleanShortName(value, prompt.displayName)
        val saved = latestSettings.contacts.any { it.id == prompt.rowId }
        renamePrompt = null
        publish()
        if (!saved) {
            pendingShort[prompt.rowId] = cleaned
            publish()
            return
        }
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    contacts = current.contacts.map { contact ->
                        if (contact.id == prompt.rowId) contact.copy(shortName = cleaned) else contact
                    },
                )
            }
        }
    }

    fun dismissRename() {
        renamePrompt = null
        publish()
    }

    fun setCustomPhoto(rowId: String, fileName: String) {
        val saved = latestSettings.contacts.any { it.id == rowId }
        if (!saved) {
            pendingPhoto[rowId]?.let { previous ->
                if (previous != fileName) tilePhotos.delete(previous)
            }
            pendingPhoto[rowId] = fileName
            renamePrompt = renamePrompt?.takeIf { it.rowId == rowId }?.copy(hasCustomPhoto = true) ?: renamePrompt
            publish()
            return
        }
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    contacts = current.contacts.map { contact ->
                        if (contact.id != rowId) {
                            contact
                        } else {
                            if (contact.customPhotoFile != null && contact.customPhotoFile != fileName) {
                                tilePhotos.delete(contact.customPhotoFile)
                            }
                            contact.copy(customPhotoFile = fileName)
                        }
                    },
                )
            }
            renamePrompt = renamePrompt?.takeIf { it.rowId == rowId }?.copy(hasCustomPhoto = true) ?: renamePrompt
            refresh()
        }
    }

    fun clearCustomPhoto(rowId: String) {
        val saved = latestSettings.contacts.find { it.id == rowId }
        if (saved == null) {
            tilePhotos.delete(pendingPhoto.remove(rowId))
            renamePrompt = renamePrompt?.takeIf { it.rowId == rowId }?.copy(hasCustomPhoto = false) ?: renamePrompt
            publish()
            return
        }
        viewModelScope.launch {
            tilePhotos.delete(saved.customPhotoFile)
            store.update { current ->
                current.copy(
                    contacts = current.contacts.map { contact ->
                        if (contact.id == rowId) contact.copy(customPhotoFile = null) else contact
                    },
                )
            }
            renamePrompt = renamePrompt?.takeIf { it.rowId == rowId }?.copy(hasCustomPhoto = false) ?: renamePrompt
            refresh()
        }
    }

    fun requestCall(tile: Tile) {
        callPhase = CallPhase.NeedsPermission(tile)
        publish()
    }

    fun onCallPermissionResult(granted: Boolean) {
        val pending = callPhase as? CallPhase.NeedsPermission ?: return
        callPhase = if (granted) CallPhase.Counting(pending.tile) else CallPhase.Idle
        if (!granted) notify(NoticeKind.CallDenied)
        publish()
    }

    fun finishCountdown(): Tile? {
        val counting = callPhase as? CallPhase.Counting
        callPhase = CallPhase.Idle
        publish()
        return counting?.tile
    }

    fun cancelCall() {
        callPhase = CallPhase.Idle
        publish()
    }

    fun onWhatsApp(tile: Tile) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            notify(NoticeKind.ContactsDenied)
            return
        }
        viewModelScope.launch {
            val link = withContext(Dispatchers.IO) {
                runCatching {
                    phoneBook.findWhatsApp(tile.contactId, tile.lookupKey, tile.phoneNumber)
                }.getOrNull()
            }
            if (link == null) notify(NoticeKind.WhatsAppMissing) else _whatsApp.tryEmit(link)
        }
    }

    fun notify(kind: NoticeKind, detail: String? = null) {
        _notices.tryEmit(NoticeEvent(kind, detail))
    }

    override fun onCleared() {
        if (watching) {
            runCatching {
                getApplication<Application>().contentResolver.unregisterContentObserver(observer)
            }
        }
        super.onCleared()
    }

    private fun addContact(rowId: String, number: String) {
        val device = latestDevices.find { it.id == rowId } ?: return
        val shortName = cleanShortName(
            pendingShort[rowId] ?: defaultShortName(device.displayName),
            device.displayName,
        )
        viewModelScope.launch {
            store.update { current ->
                if (current.contacts.any { it.id == device.id }) return@update current
                val contact = SavedContact(
                    id = device.id,
                    contactId = device.contactId,
                    lookupKey = device.lookupKey,
                    displayName = device.displayName.ifBlank { shortName },
                    shortName = shortName,
                    phoneNumber = number,
                    addedAt = System.currentTimeMillis(),
                    customPhotoFile = pendingPhoto[device.id],
                )
                val order = if (current.frozen) {
                    (current.frozenOrder + contact.id).distinct()
                } else {
                    current.frozenOrder
                }
                current.copy(contacts = current.contacts + contact, frozenOrder = order)
            }
            refresh()
        }
    }

    private fun updateSettings(block: (QuickDialSettings) -> QuickDialSettings) {
        viewModelScope.launch { store.update(block) }
    }

    private fun currentTiles(): List<Tile> {
        val order = orderedIds(latestSettings.orderRequest(latestCounts))
        val byId = latestSettings.contacts.associateBy { it.id }
        return order.mapNotNull { id ->
            val contact = byId[id] ?: return@mapNotNull null
            Tile(
                id = contact.id,
                contactId = contact.contactId,
                lookupKey = contact.lookupKey,
                displayName = contact.displayName,
                shortName = contact.shortName.ifBlank { defaultShortName(contact.displayName) },
                phoneNumber = contact.phoneNumber,
                callCount = latestCounts[contact.id] ?: 0,
                photoUri = photos[contact.id],
            )
        }
    }

    private fun publish() {
        if (screen == Screen.Reorder && !latestSettings.frozen) screen = Screen.Home
        val tiles = currentTiles()
        val hasSaved = latestSettings.contacts.isNotEmpty()
        _ui.value = HomeUi(
            tiles = tiles,
            columns = latestSettings.columns,
            tileRatio = latestSettings.tileRatio,
            frozen = latestSettings.frozen,
            showBadge = latestSettings.showCallBadge,
            shortNameSp = ShortNameSize.fromKey(latestSettings.shortNameSize).sp,
            theme = DialTheme.fromKey(latestSettings.theme),
            callPhase = callPhase,
            screen = screen,
            contactsDenied = ready && hasSaved && !contactsGranted,
            callLogDenied = ready && hasSaved && !callLogGranted,
            ready = ready,
        )
        _picker.value = buildPicker()
    }

    private fun buildPicker(): PickerUi {
        val selected = latestSettings.contacts.associateBy { it.id }
        val rows = latestDevices.mapNotNull { device ->
            val numbers = device.phones.map { it.number }
            if (!matchesContactQuery(device.displayName, numbers, query)) return@mapNotNull null
            val saved = selected[device.id]
            val phones = labeledPhones(device)
            PickerRow(
                id = device.id,
                displayName = device.displayName.ifBlank { phones.firstOrNull()?.number.orEmpty() },
                phones = phones,
                selected = saved != null,
                selectedNumber = saved?.phoneNumber,
                shortName = saved?.shortName
                    ?: pendingShort[device.id]
                    ?: defaultShortName(device.displayName),
            )
        }.sortedWith { left, right ->
            val byName = nameOrder(left.displayName, right.displayName)
            if (byName != 0) byName else left.id.compareTo(right.id)
        }
        return PickerUi(
            query = query,
            rows = rows,
            loading = !ready,
            numberPrompt = numberPrompt,
            renamePrompt = renamePrompt,
        )
    }

    private fun labeledPhones(device: DeviceContact): List<LabeledNumber> {
        val options = device.phones.map {
            NumberOption(
                number = it.number,
                superPrimary = it.superPrimary,
                primary = it.primary,
                mobile = it.type == Phone.TYPE_MOBILE,
            )
        }
        val distinct = distinctNumbers(options)
        val suggested = suggestedIndex(distinct)
        return distinct.mapIndexed { index, option ->
            val source = device.phones.firstOrNull { samePhone(it.number, option.number) }
            LabeledNumber(
                number = option.number,
                label = phoneLabel(source?.type ?: Phone.TYPE_OTHER, source?.customLabel),
                suggested = index == suggested,
            )
        }
    }

    private fun resolvePhoto(contact: SavedContact, device: DeviceContact?): String? {
        val custom = contact.customPhotoFile?.let { tilePhotos.fileUri(it) }
        if (custom != null) return custom
        return device?.photoUri
    }

    private fun findDevice(devices: List<DeviceContact>, saved: SavedContact): DeviceContact? =
        devices.find { it.id == saved.id }
            ?: devices.find { saved.lookupKey.isNotBlank() && it.lookupKey == saved.lookupKey }
            ?: devices.find { it.contactId == saved.contactId }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    private fun watchCallLog() {
        if (watching || !callLogGranted) return
        runCatching {
            getApplication<Application>().contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer,
            )
            watching = true
        }
    }
}
