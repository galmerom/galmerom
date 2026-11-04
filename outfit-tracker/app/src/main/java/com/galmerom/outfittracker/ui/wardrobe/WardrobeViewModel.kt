package com.galmerom.outfittracker.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.outfittracker.domain.BackgroundRemover
import com.galmerom.outfittracker.domain.logic.WardrobeRules
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.repository.CategoryRepository
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

data class PhotoImproveState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val updated: Int = 0,
    val finished: Boolean = false,
    val empty: Boolean = false,
)

class WardrobeViewModel(
    private val clothing: ClothingRepository,
    private val remover: BackgroundRemover,
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
    private val _improve = MutableStateFlow(PhotoImproveState())
    val improve: StateFlow<PhotoImproveState> = _improve

    fun improvePhotos() {
        if (_improve.value.running) return
        val targets = items.value.filter { item ->
            !item.hidden && !item.photoUrl.isNullOrBlank() && item.originalPhotoUrl.isNullOrBlank()
        }
        if (targets.isEmpty()) {
            _improve.value = PhotoImproveState(empty = true)
            return
        }
        viewModelScope.launch {
            var updated = 0
            targets.forEachIndexed { index, item ->
                _improve.value = PhotoImproveState(
                    running = true,
                    done = index,
                    total = targets.size,
                    updated = updated,
                )
                val source = item.photoUrl ?: return@forEachIndexed
                val bytes = clothing.downloadPhoto(source).getOrNull() ?: return@forEachIndexed
                val processed = remover.prepare(bytes).processed ?: return@forEachIndexed
                if (clothing.saveCutout(item.id, processed, source).isSuccess) updated++
            }
            _improve.value = PhotoImproveState(
                done = targets.size,
                total = targets.size,
                updated = updated,
                finished = true,
            )
        }
    }

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
    val categoryId: String = "",
    val categories: List<ClothingCategory> = emptyList(),
    val hidden: Boolean = false,
    val photoUrl: String? = null,
    val originalPhotoUrl: String? = null,
    val backgroundRemoved: Boolean = false,
    val photoJpeg: ByteArray? = null,
    val originalJpeg: ByteArray? = null,
    val processedJpeg: ByteArray? = null,
    val pendingNewPhoto: Boolean = false,
    val preparingPhoto: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val savedItemId: String? = null,
)

class ClothingEditViewModel(
    private val itemId: String,
    private val clothing: ClothingRepository,
    categories: CategoryRepository,
    private val remover: BackgroundRemover,
    private val initialCategoryId: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ClothingEditUiState(ready = itemId == NEW_ID))
    val state: StateFlow<ClothingEditUiState> = _state

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var loaded = itemId == NEW_ID

    init {
        viewModelScope.launch {
            categories.observeAll().catch { _messages.emit(it.toUserMessage()) }.collect { list ->
                _state.update { state ->
                    val preferred = state.categoryId.ifBlank {
                        initialCategoryId?.takeIf { id -> list.any { it.id == id } }.orEmpty()
                    }.ifBlank { list.firstOrNull()?.id.orEmpty() }
                    state.copy(categories = list, categoryId = preferred)
                }
            }
        }
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
                                categoryId = item.categoryId,
                                hidden = item.hidden,
                                photoUrl = item.photoUrl,
                                originalPhotoUrl = item.originalPhotoUrl,
                                backgroundRemoved = item.backgroundRemoved,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onDescription(value: String) = _state.update { it.copy(description = value) }

    fun onCategory(value: String) = _state.update { it.copy(categoryId = value) }

    fun onHidden(value: Boolean) = _state.update { it.copy(hidden = value) }

    fun onPhoto(jpeg: ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(preparingPhoto = true) }
            val prepared = remover.prepare(jpeg)
            _state.update {
                it.copy(
                    preparingPhoto = false,
                    pendingNewPhoto = true,
                    originalJpeg = prepared.original,
                    processedJpeg = prepared.processed,
                    photoJpeg = prepared.processed ?: prepared.original,
                    backgroundRemoved = prepared.processed != null,
                )
            }
            if (prepared.processed == null) _messages.emit(UserMessage.CUTOUT_KEPT_ORIGINAL)
        }
    }

    fun setBackgroundRemoved(value: Boolean) {
        val current = _state.value
        if (current.pendingNewPhoto) {
            val jpeg = if (value) current.processedJpeg else current.originalJpeg
            if (jpeg == null) return
            _state.update { it.copy(backgroundRemoved = value, photoJpeg = jpeg) }
        } else if (!current.originalPhotoUrl.isNullOrBlank()) {
            _state.update { it.copy(backgroundRemoved = value) }
        }
    }

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
            val displayJpeg = if (current.pendingNewPhoto) {
                if (current.backgroundRemoved) current.processedJpeg else current.originalJpeg
            } else {
                null
            }
            val originalJpeg = if (current.pendingNewPhoto && current.backgroundRemoved) current.originalJpeg else null
            val result = clothing.upsert(
                ClothingItem(
                    id = existingId,
                    description = current.description,
                    categoryId = current.categoryId,
                    photoUrl = current.photoUrl,
                    hidden = current.hidden,
                    originalPhotoUrl = if (current.pendingNewPhoto) null else current.originalPhotoUrl,
                    backgroundRemoved = if (current.pendingNewPhoto) false else current.backgroundRemoved,
                ),
                displayJpeg,
                originalJpeg,
            )
            result.fold(
                onSuccess = { (item, outcome) ->
                    _state.update { it.copy(saving = false, saved = true, savedItemId = item.id) }
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
