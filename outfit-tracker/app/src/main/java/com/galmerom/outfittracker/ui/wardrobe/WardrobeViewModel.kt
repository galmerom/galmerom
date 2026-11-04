package com.galmerom.outfittracker.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.outfittracker.domain.logic.WardrobeRules
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.SaveOutcome
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WardrobeViewModel(
    private val clothing: ClothingRepository,
) : ViewModel() {
    val items: StateFlow<List<ClothingItem>> = clothing.observeAll()
        .catch { report(it.toUserMessage()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visible: StateFlow<List<ClothingItem>> = items
        .map { WardrobeRules.selectable(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hidden: StateFlow<List<ClothingItem>> = items
        .map { list -> list.filter { it.hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch {
            clothing.setHidden(id, hidden).onFailure { report(it.toUserMessage()) }
        }
    }

    private fun report(message: UserMessage) {
        _messages.tryEmit(message)
    }
}

data class ClothingEditUiState(
    val ready: Boolean = false,
    val description: String = "",
    val category: ClothingCategory = ClothingCategory.SHIRT,
    val hidden: Boolean = false,
    val photoUrl: String? = null,
    val photoJpeg: ByteArray? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

class ClothingEditViewModel(
    private val itemId: String,
    private val clothing: ClothingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClothingEditUiState(ready = itemId == NEW_ID))
    val state: StateFlow<ClothingEditUiState> = _state

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var loaded = itemId == NEW_ID

    init {
        if (itemId != NEW_ID) {
            viewModelScope.launch {
                clothing.observeAll().catch { _messages.emit(it.toUserMessage()) }.collect { items ->
                    val item = items.find { it.id == itemId } ?: return@collect
                    if (!loaded) {
                        loaded = true
                        _state.update {
                            it.copy(
                                ready = true,
                                description = item.description,
                                category = item.category,
                                hidden = item.hidden,
                                photoUrl = item.photoUrl,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onDescription(value: String) = _state.update { it.copy(description = value) }

    fun onCategory(value: ClothingCategory) = _state.update { it.copy(category = value) }

    fun onHidden(value: Boolean) = _state.update { it.copy(hidden = value) }

    fun onPhoto(jpeg: ByteArray) = _state.update { it.copy(photoJpeg = jpeg) }

    fun reportGeneric() {
        _messages.tryEmit(UserMessage.GENERIC)
    }

    fun reportCameraDenied() {
        _messages.tryEmit(UserMessage.CAMERA_PERMISSION)
    }

    fun save() {
        val current = _state.value
        if (current.description.trim().isEmpty()) {
            _messages.tryEmit(UserMessage.NEED_DESCRIPTION)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val existingId = if (itemId == NEW_ID) "" else itemId
            val result = clothing.upsert(
                ClothingItem(
                    id = existingId,
                    description = current.description,
                    category = current.category,
                    photoUrl = current.photoUrl,
                    hidden = current.hidden,
                ),
                current.photoJpeg,
            )
            result.fold(
                onSuccess = { (_, outcome) ->
                    _state.update { it.copy(saving = false, saved = true) }
                    _messages.emit(
                        if (outcome == SaveOutcome.SAVED_WITHOUT_PHOTO) {
                            UserMessage.SAVED_WITHOUT_PHOTO
                        } else {
                            UserMessage.CLOTHING_SAVED
                        },
                    )
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false) }
                    _messages.emit(error.toUserMessage())
                },
            )
        }
    }

    companion object {
        const val NEW_ID = "new"
    }
}
