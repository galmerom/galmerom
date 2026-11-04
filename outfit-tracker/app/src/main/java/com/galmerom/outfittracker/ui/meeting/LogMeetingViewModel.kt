package com.galmerom.outfittracker.ui.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.logic.EntitySuggestions
import com.galmerom.outfittracker.domain.logic.WardrobeRules
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.DetectedGarment
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.MeetingDraft
import com.galmerom.outfittracker.domain.model.SaveOutcome
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.toUserMessage
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSuggestion(
    val garment: DetectedGarment,
    val match: ClothingItem?,
)

data class LogMeetingUiState(
    val date: LocalDate = LocalDate.now(),
    val entityQuery: String = "",
    val entities: List<Entity> = emptyList(),
    val selectedEntity: Entity? = null,
    val newEntityType: EntityType = EntityType.PERSON,
    val wardrobe: List<ClothingItem> = emptyList(),
    val selectedClothingIds: Set<String> = emptySet(),
    val note: String = "",
    val meetingPhoto: ByteArray? = null,
    val detectionPhoto: ByteArray? = null,
    val aiSuggestions: List<AiSuggestion> = emptyList(),
    val detecting: Boolean = false,
    val saving: Boolean = false,
    val creatingEntity: Boolean = false,
) {
    val suggestions: List<Entity> get() = EntitySuggestions.match(entityQuery, entities)
    val offerCreate: Boolean get() = EntitySuggestions.shouldOfferCreate(entityQuery, entities)
    val selectable: List<ClothingItem> get() = WardrobeRules.selectable(wardrobe)
}

class LogMeetingViewModel(
    private val clothing: ClothingRepository,
    private val entities: EntityRepository,
    private val meetings: MeetingRepository,
    private val detector: GarmentDetector,
) : ViewModel() {
    private val _state = MutableStateFlow(LogMeetingUiState())
    val state: StateFlow<LogMeetingUiState> = _state

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            clothing.observeAll().catch { report(it.toUserMessage()) }.collect { items ->
                _state.update { it.copy(wardrobe = items) }
            }
        }
        viewModelScope.launch {
            entities.observeAll().catch { report(it.toUserMessage()) }.collect { list ->
                _state.update { it.copy(entities = list) }
            }
        }
    }

    fun onDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun onQuery(value: String) = _state.update { state ->
        val stillSelected = state.selectedEntity?.takeIf { it.name == value }
        state.copy(entityQuery = value, selectedEntity = stillSelected)
    }

    fun selectEntity(entity: Entity) = _state.update {
        it.copy(selectedEntity = entity, entityQuery = entity.name)
    }

    fun clearEntity() = _state.update { it.copy(selectedEntity = null, entityQuery = "") }

    fun onEntityType(type: EntityType) = _state.update { it.copy(newEntityType = type) }

    fun onNote(value: String) = _state.update { it.copy(note = value) }

    fun onMeetingPhoto(jpeg: ByteArray) = _state.update { it.copy(meetingPhoto = jpeg) }

    fun onDetectionPhoto(jpeg: ByteArray) = _state.update { it.copy(detectionPhoto = jpeg, aiSuggestions = emptyList()) }

    fun toggleClothing(item: ClothingItem) = _state.update { state ->
        val ids = state.selectedClothingIds.toMutableSet()
        if (!ids.add(item.id)) ids.remove(item.id)
        state.copy(selectedClothingIds = ids)
    }

    fun removeClothing(id: String) = _state.update { state ->
        state.copy(selectedClothingIds = state.selectedClothingIds - id)
    }

    fun createEntity() {
        val name = _state.value.entityQuery.trim()
        val type = _state.value.newEntityType
        if (name.isEmpty() || _state.value.creatingEntity) return
        viewModelScope.launch {
            _state.update { it.copy(creatingEntity = true) }
            entities.create(name, type).fold(
                onSuccess = { entity ->
                    _state.update {
                        it.copy(
                            creatingEntity = false,
                            selectedEntity = entity,
                            entityQuery = entity.name,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(creatingEntity = false) }
                    report(error.toUserMessage())
                },
            )
        }
    }

    fun addManual(description: String, category: ClothingCategory, photo: ByteArray?) {
        if (description.trim().isEmpty()) {
            report(UserMessage.NEED_DESCRIPTION)
            return
        }
        viewModelScope.launch {
            clothing.upsert(
                ClothingItem(
                    id = "",
                    description = description,
                    category = category,
                    photoUrl = null,
                    hidden = false,
                ),
                photo,
            ).fold(
                onSuccess = { (item, outcome) ->
                    _state.update { it.copy(selectedClothingIds = it.selectedClothingIds + item.id) }
                    report(
                        if (outcome == SaveOutcome.SAVED_WITHOUT_PHOTO) {
                            UserMessage.SAVED_WITHOUT_PHOTO
                        } else {
                            UserMessage.CLOTHING_SAVED
                        },
                    )
                },
                onFailure = { report(it.toUserMessage()) },
            )
        }
    }

    fun detect() {
        val jpeg = _state.value.detectionPhoto ?: return
        if (_state.value.detecting) return
        viewModelScope.launch {
            _state.update { it.copy(detecting = true) }
            detector.detect(jpeg).fold(
                onSuccess = { garments ->
                    if (garments.isEmpty()) {
                        _state.update { it.copy(detecting = false, aiSuggestions = emptyList()) }
                        report(UserMessage.AI_EMPTY)
                    } else {
                        val wardrobe = _state.value.wardrobe
                        _state.update {
                            it.copy(
                                detecting = false,
                                aiSuggestions = garments.map { garment ->
                                    AiSuggestion(garment, WardrobeRules.match(garment, wardrobe))
                                },
                            )
                        }
                    }
                },
                onFailure = {
                    _state.update { it.copy(detecting = false, aiSuggestions = emptyList()) }
                    report(UserMessage.AI_FAILED)
                },
            )
        }
    }

    fun acceptSuggestion(index: Int) {
        val suggestion = _state.value.aiSuggestions.getOrNull(index) ?: return
        viewModelScope.launch {
            val existing = suggestion.match
            val item = if (existing != null) {
                existing
            } else {
                val created = clothing.upsert(
                    ClothingItem(
                        id = "",
                        description = suggestion.garment.description,
                        category = suggestion.garment.category,
                        photoUrl = null,
                        hidden = false,
                    ),
                    photoJpeg = null,
                ).getOrElse {
                    report(it.toUserMessage())
                    return@launch
                }
                created.first
            }
            _state.update { state ->
                state.copy(
                    selectedClothingIds = state.selectedClothingIds + item.id,
                    aiSuggestions = state.aiSuggestions.filterIndexed { suggestionIndex, _ -> suggestionIndex != index },
                )
            }
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val entity = current.selectedEntity ?: current.entities.singleOrNull { candidate ->
            candidate.name.equals(current.entityQuery.trim(), ignoreCase = true)
        }
        if (entity == null) {
            report(UserMessage.NEED_ENTITY)
            return
        }
        if (current.selectedClothingIds.isEmpty()) {
            report(UserMessage.NEED_CLOTHES)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, selectedEntity = entity) }
            meetings.log(
                MeetingDraft(
                    date = current.date,
                    entityId = entity.id,
                    entityName = entity.name,
                    clothingItemIds = current.selectedClothingIds.toList(),
                    note = current.note,
                ),
                current.meetingPhoto,
            ).fold(
                onSuccess = { outcome ->
                    _state.value = LogMeetingUiState(
                        entities = _state.value.entities,
                        wardrobe = _state.value.wardrobe,
                    )
                    report(
                        if (outcome == SaveOutcome.SAVED_WITHOUT_PHOTO) {
                            UserMessage.SAVED_WITHOUT_PHOTO
                        } else {
                            UserMessage.MEETING_SAVED
                        },
                    )
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false) }
                    report(error.toUserMessage())
                },
            )
        }
    }

    fun notify(message: UserMessage) = report(message)

    private fun report(message: UserMessage) {
        _messages.tryEmit(message)
    }
}
