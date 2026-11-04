package com.galmerom.outfittracker.ui.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.WardrobeDetection
import com.galmerom.outfittracker.domain.logic.EntitySuggestions
import com.galmerom.outfittracker.domain.logic.WardrobeRules
import com.galmerom.outfittracker.domain.logic.RepeatWarnings
import com.galmerom.outfittracker.domain.logic.meetingCounts
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingDraft
import com.galmerom.outfittracker.domain.model.SaveOutcome
import com.galmerom.outfittracker.domain.repository.CategoryRepository
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import java.util.UUID
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

enum class DetectionNotice {
    None,
    NoMatch,
    Failed,
}

data class AiSuggestion(
    val key: String,
    val itemId: String? = null,
    val categoryId: String? = null,
) {
    val unmatched: Boolean get() = itemId == null
}

data class ClothingPickRow(
    val key: String,
    val categoryId: String = "",
    val query: String = "",
    val itemId: String? = null,
)

data class LogMeetingUiState(
    val date: LocalDate = LocalDate.now(),
    val entityQuery: String = "",
    val entities: List<Entity> = emptyList(),
    val selectedEntities: List<Entity> = emptyList(),
    val editingId: String? = null,
    val existingPhotoUrl: String? = null,
    val newEntityType: EntityType = EntityType.PERSON,
    val wardrobe: List<ClothingItem> = emptyList(),
    val categories: List<ClothingCategory> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val rows: List<ClothingPickRow> = emptyList(),
    val aiSelectedIds: Set<String> = emptySet(),
    val selectedClothingIds: Set<String> = emptySet(),
    val note: String = "",
    val meetingPhoto: ByteArray? = null,
    val aiSuggestions: List<AiSuggestion> = emptyList(),
    val detecting: Boolean = false,
    val detectionNotice: DetectionNotice = DetectionNotice.None,
    val saving: Boolean = false,
    val creatingEntity: Boolean = false,
) {
    val suggestions: List<Entity>
        get() = if (entityQuery.isBlank()) {
            emptyList()
        } else {
            EntitySuggestions.forPicker(entityQuery, entities, meetingCounts(meetings))
        }
    val offerCreate: Boolean get() = EntitySuggestions.shouldOfferCreate(entityQuery, entities)
    val selectable: List<ClothingItem> get() = WardrobeRules.selectable(wardrobe)
    val warnings: Map<String, com.galmerom.outfittracker.domain.logic.RepeatWarning>
        get() = RepeatWarnings.forSelection(
            selectedEntityIds = selectedEntities.map { it.id }.toSet(),
            selectedItemIds = selectedClothingIds,
            entities = entities,
            meetings = meetings,
            excludeMeetingId = editingId,
        )

    fun syncSelection(): LogMeetingUiState {
        val ids = rows.mapNotNull { it.itemId }.toSet() + aiSelectedIds
        return copy(selectedClothingIds = ids)
    }

    fun hasEnteredData(today: LocalDate = LocalDate.now()): Boolean =
        editingId != null ||
            selectedEntities.isNotEmpty() ||
            note.isNotBlank() ||
            rows.isNotEmpty() ||
            selectedClothingIds.isNotEmpty() ||
            meetingPhoto != null ||
            !existingPhotoUrl.isNullOrBlank() ||
            entityQuery.isNotBlank() ||
            aiSuggestions.isNotEmpty() ||
            date != today
}

class LogMeetingViewModel(
    private val clothing: ClothingRepository,
    private val entities: EntityRepository,
    private val meetings: MeetingRepository,
    private val categoriesRepo: CategoryRepository,
    private val detector: GarmentDetector,
    private val editingMeetingId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(LogMeetingUiState())
    val state: StateFlow<LogMeetingUiState> = _state

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished = _finished.asSharedFlow()

    init {
        viewModelScope.launch {
            clothing.observeAll().catch { report(it.toUserMessage()) }.collect { items ->
                _state.update { it.copy(wardrobe = items) }
                maybeLoadExisting()
            }
        }
        viewModelScope.launch {
            entities.observeAll().catch { report(it.toUserMessage()) }.collect { list ->
                _state.update { it.copy(entities = list) }
                maybeLoadExisting()
            }
        }
        viewModelScope.launch {
            meetings.observeAll().catch { report(it.toUserMessage()) }.collect { list ->
                _state.update { it.copy(meetings = list) }
                maybeLoadExisting()
            }
        }
        viewModelScope.launch {
            categoriesRepo.observeAll().catch { report(it.toUserMessage()) }.collect { list ->
                _state.update { it.copy(categories = list) }
            }
        }
    }

    fun addClothingRow() = _state.update { state ->
        state.copy(rows = state.rows + ClothingPickRow(UUID.randomUUID().toString())).syncSelection()
    }

    fun removeClothingRow(key: String) = _state.update { state ->
        state.copy(rows = state.rows.filterNot { it.key == key }).syncSelection()
    }

    fun onRowCategory(key: String, categoryId: String) = _state.update { state ->
        state.copy(
            rows = state.rows.map { row ->
                if (row.key == key) row.copy(categoryId = categoryId, itemId = null) else row
            },
        ).syncSelection()
    }

    fun onRowQuery(key: String, query: String) = _state.update { state ->
        state.copy(
            rows = state.rows.map { row ->
                if (row.key == key) row.copy(query = query, itemId = null) else row
            },
        ).syncSelection()
    }

    fun selectRowItem(key: String, item: ClothingItem) = _state.update { state ->
        state.copy(
            rows = state.rows.map { row ->
                if (row.key == key) {
                    row.copy(itemId = item.id, query = item.description, categoryId = item.categoryId)
                } else {
                    row
                }
            },
        ).syncSelection()
    }

    fun onDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun onQuery(value: String) = _state.update { it.copy(entityQuery = value) }

    fun selectEntity(entity: Entity) = _state.update { state ->
        val next = if (state.selectedEntities.any { it.id == entity.id }) {
            state.selectedEntities
        } else {
            state.selectedEntities + entity
        }
        state.copy(selectedEntities = next, entityQuery = "")
    }

    fun removeEntity(id: String) = _state.update { state ->
        state.copy(selectedEntities = state.selectedEntities.filterNot { it.id == id })
    }

    fun onEntityType(type: EntityType) = _state.update { it.copy(newEntityType = type) }

    fun onNote(value: String) = _state.update { it.copy(note = value) }

    fun onOutfitPhoto(jpeg: ByteArray) {
        _state.update {
            it.copy(
                meetingPhoto = jpeg,
                aiSuggestions = emptyList(),
                detectionNotice = DetectionNotice.None,
            )
        }
        detect()
    }

    fun toggleClothing(item: ClothingItem) = _state.update { state ->
        val ids = state.selectedClothingIds.toMutableSet()
        if (!ids.add(item.id)) ids.remove(item.id)
        state.copy(selectedClothingIds = ids)
    }

    fun removeClothing(id: String) = _state.update { state ->
        state.copy(
            aiSelectedIds = state.aiSelectedIds - id,
            rows = state.rows.map { row -> if (row.itemId == id) row.copy(itemId = null) else row },
        ).syncSelection()
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
                            selectedEntities = it.selectedEntities + entity,
                            entityQuery = "",
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

    fun addManual(description: String, categoryId: String, photo: ByteArray?) {
        if (description.trim().isEmpty()) {
            report(UserMessage.NEED_DESCRIPTION)
            return
        }
        viewModelScope.launch {
            clothing.upsert(
                ClothingItem(
                    id = "",
                    description = description,
                    categoryId = categoryId,
                    photoUrl = null,
                    hidden = false,
                ),
                photo,
            ).fold(
                onSuccess = { (item, outcome) ->
                    _state.update { it.copy(aiSelectedIds = it.aiSelectedIds + item.id).syncSelection() }
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
        val jpeg = _state.value.meetingPhoto ?: return
        if (_state.value.detecting) return
        val known = WardrobeRules.selectable(_state.value.wardrobe)
        viewModelScope.launch {
            _state.update { it.copy(detecting = true, detectionNotice = DetectionNotice.None) }
            detector.detect(jpeg, known, _state.value.categories).fold(
                onSuccess = { detection -> _state.update { it.withDetection(detection) } },
                onFailure = {
                    _state.update {
                        it.copy(
                            detecting = false,
                            detectionNotice = DetectionNotice.Failed,
                            aiSuggestions = emptyList(),
                        )
                    }
                },
            )
        }
    }

    fun acceptSuggestion(itemId: String) {
        if (_state.value.wardrobe.none { it.id == itemId }) return
        _state.update { state ->
            state.copy(
                aiSelectedIds = state.aiSelectedIds + itemId,
                aiSuggestions = state.aiSuggestions.filterNot { it.itemId == itemId },
            ).syncSelection()
        }
    }

    fun dismissSuggestion(key: String) = _state.update { state ->
        state.copy(aiSuggestions = state.aiSuggestions.filterNot { it.key == key })
    }

    fun attachCreatedItem(itemId: String, suggestionKey: String?) {
        if (itemId.isBlank()) return
        _state.update { state ->
            state.copy(
                aiSelectedIds = state.aiSelectedIds + itemId,
                aiSuggestions = state.aiSuggestions.filterNot { suggestionKey != null && it.key == suggestionKey },
            ).syncSelection()
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (current.selectedEntities.isEmpty()) {
            report(UserMessage.NEED_ENTITY)
            return
        }
        if (current.selectedClothingIds.isEmpty()) {
            report(UserMessage.NEED_CLOTHES)
            return
        }
        val draft = MeetingDraft(
            date = current.date,
            entityIds = current.selectedEntities.map { it.id },
            entityNames = current.selectedEntities.map { it.name },
            clothingItemIds = current.selectedClothingIds.toList(),
            note = current.note,
        )
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = if (editingMeetingId == null) {
                meetings.log(draft, current.meetingPhoto)
            } else {
                meetings.update(editingMeetingId, draft, current.meetingPhoto, current.existingPhotoUrl)
            }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false) }
                    _finished.tryEmit(Unit)
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false) }
                    report(error.toUserMessage())
                },
            )
        }
    }

    private var hydrated = false

    private fun maybeLoadExisting() {
        val id = editingMeetingId ?: return
        if (hydrated) return
        val current = _state.value
        val meeting = current.meetings.find { it.id == id } ?: return
        if (meeting.entityIds.isNotEmpty() && current.entities.isEmpty()) return
        if (meeting.clothingItemIds.isNotEmpty() && current.wardrobe.isEmpty()) return
        hydrated = true
        val selected = meeting.entityIds.mapIndexed { index, entityId ->
            current.entities.find { it.id == entityId } ?: Entity(
                id = entityId,
                name = meeting.entityNames.getOrNull(index).orEmpty().ifBlank { entityId },
                type = EntityType.PERSON,
            )
        }
        val rows = meeting.clothingItemIds.map { itemId ->
            val item = current.wardrobe.find { it.id == itemId }
            ClothingPickRow(
                key = itemId,
                categoryId = item?.categoryId.orEmpty(),
                query = item?.description.orEmpty(),
                itemId = itemId,
            )
        }
        _state.update {
            it.copy(
                date = meeting.date,
                selectedEntities = selected,
                rows = rows,
                note = meeting.note,
                existingPhotoUrl = meeting.photoUrl,
                editingId = meeting.id,
            ).syncSelection()
        }
    }

    private fun LogMeetingUiState.withDetection(detection: WardrobeDetection): LogMeetingUiState {
        val matches = detection.matchedIds.map { id -> AiSuggestion(key = id, itemId = id) }
        val missing = detection.unmatchedCategories.map { categoryId ->
            AiSuggestion(key = UUID.randomUUID().toString(), categoryId = categoryId)
        }
        val suggestions = matches + missing
        return copy(
            detecting = false,
            detectionNotice = if (suggestions.isEmpty()) DetectionNotice.NoMatch else DetectionNotice.None,
            aiSuggestions = suggestions,
        )
    }

    fun notify(message: UserMessage) = report(message)

    private fun report(message: UserMessage) {
        _messages.tryEmit(message)
    }
}
