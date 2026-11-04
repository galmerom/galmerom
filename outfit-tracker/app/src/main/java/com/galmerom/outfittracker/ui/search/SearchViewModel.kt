package com.galmerom.outfittracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galmerom.outfittracker.domain.logic.MeetingSearch
import com.galmerom.outfittracker.domain.logic.MeetingWindow
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingHit
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val pages: Int = 1,
    val meetings: List<Meeting> = emptyList(),
    val entities: List<Entity> = emptyList(),
) {
    val filtered: List<MeetingHit> get() = MeetingSearch.search(meetings, entities, query)
    val visible: List<MeetingHit> get() = filtered.take(pages.coerceAtLeast(1) * MeetingWindow.PAGE_SIZE)
    val hasMore: Boolean get() = filtered.size > pages.coerceAtLeast(1) * MeetingWindow.PAGE_SIZE
}

class SearchViewModel(
    meetings: MeetingRepository,
    entities: EntityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            meetings.observeAll().catch { _messages.tryEmit(it.toUserMessage()) }.collect { list ->
                _state.update { it.copy(meetings = list) }
            }
        }
        viewModelScope.launch {
            entities.observeAll().catch { _messages.tryEmit(it.toUserMessage()) }.collect { list ->
                _state.update { it.copy(entities = list) }
            }
        }
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value, pages = 1) }

    fun showMore() = _state.update { it.copy(pages = it.pages + 1) }
}

data class MeetingDetailUiState(
    val meetingsLoaded: Boolean = false,
    val meeting: Meeting? = null,
    val clothes: Map<String, ClothingItem> = emptyMap(),
)

class MeetingDetailViewModel(
    private val meetingId: String,
    meetings: MeetingRepository,
    clothing: ClothingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MeetingDetailUiState())
    val state: StateFlow<MeetingDetailUiState> = _state

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            meetings.observeAll().catch { _messages.tryEmit(it.toUserMessage()) }.collect { list ->
                _state.update { state ->
                    state.copy(
                        meetingsLoaded = true,
                        meeting = list.find { it.id == meetingId },
                    )
                }
            }
        }
        viewModelScope.launch {
            clothing.observeAll().catch { _messages.tryEmit(it.toUserMessage()) }.collect { items ->
                _state.update { it.copy(clothes = items.associateBy { item -> item.id }) }
            }
        }
    }
}
