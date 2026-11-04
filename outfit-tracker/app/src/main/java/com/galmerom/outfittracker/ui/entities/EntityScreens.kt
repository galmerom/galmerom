package com.galmerom.outfittracker.ui.entities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.logic.EntitySuggestions
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.common.EmptyState
import com.galmerom.outfittracker.ui.common.appCardColors
import com.galmerom.outfittracker.ui.common.appCardElevation
import com.galmerom.outfittracker.ui.common.appTextFieldColors
import com.galmerom.outfittracker.ui.common.labelRes
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.toUserMessage
import com.galmerom.outfittracker.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun EntityListScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, onHidden: () -> Unit) {
    val repo = LocalAppContainer.current.entityRepository ?: return
    val viewModel: EntityListViewModel = viewModel(factory = viewModelFactory { EntityListViewModel(repo) })
    val entities by viewModel.visible.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.entity_add), modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextButton(onClick = onHidden) { Text(stringResource(R.string.wardrobe_hidden)) } }
            if (entities.isEmpty()) {
                item {
                    EmptyState(stringResource(R.string.entities_empty)) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(entities, key = { it.id }) { entity ->
                Card(
                    onClick = { onEdit(entity.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = appCardColors(),
                    elevation = appCardElevation(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entity.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(entity.type.labelRes()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenEntitiesScreen(onBack: () -> Unit) {
    val repo = LocalAppContainer.current.entityRepository ?: return
    val viewModel: EntityListViewModel = viewModel(factory = viewModelFactory { EntityListViewModel(repo) })
    val entities by viewModel.hidden.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
            if (entities.isEmpty()) {
                item { Text(stringResource(R.string.entities_hidden_empty)) }
            }
            items(entities, key = { it.id }) { entity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = appCardColors(),
                    elevation = appCardElevation(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entity.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(entity.type.labelRes()))
                        }
                        TextButton(onClick = { viewModel.setHidden(entity.id, false) }) {
                            Text(stringResource(R.string.clothing_revive))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EntityEditScreen(entityId: String, onBack: () -> Unit) {
    val repo = LocalAppContainer.current.entityRepository ?: return
    val viewModel: EntityEditViewModel = viewModel(
        key = entityId,
        factory = viewModelFactory { EntityEditViewModel(entityId, repo) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var newMember by remember { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.entity_name)) },
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == EntityType.PERSON,
                    onClick = { viewModel.onType(EntityType.PERSON) },
                    label = { Text(stringResource(R.string.entity_type_person)) },
                )
                FilterChip(
                    selected = state.type == EntityType.FORUM,
                    onClick = { viewModel.onType(EntityType.FORUM) },
                    label = { Text(stringResource(R.string.entity_type_forum)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.entity_hidden_help), modifier = Modifier.weight(1f))
                Switch(checked = state.hidden, onCheckedChange = viewModel::onHidden)
            }
            if (state.type == EntityType.FORUM && state.savedId != null) {
                Text(stringResource(R.string.entity_members), style = MaterialTheme.typography.titleMedium)
                val people = state.all.filter { it.type == EntityType.PERSON && !it.hidden && it.id != state.savedId }
                if (people.isEmpty()) {
                    Text(stringResource(R.string.entity_no_people), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                people.forEach { person ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = person.id in state.memberIds,
                            onCheckedChange = { checked ->
                                val next = if (checked) state.memberIds + person.id else state.memberIds - person.id
                                viewModel.setMembers(next)
                            },
                        )
                        Text(person.name)
                    }
                }
                OutlinedTextField(
                    value = newMember,
                    onValueChange = { newMember = it },
                    label = { Text(stringResource(R.string.entity_member_name)) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        viewModel.createMember(newMember)
                        newMember = ""
                    },
                    enabled = newMember.isNotBlank(),
                ) { Text(stringResource(R.string.entity_member_add)) }
            }
            TextButton(onClick = viewModel::save) { Text(stringResource(R.string.save)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

class EntityListViewModel(private val repo: EntityRepository) : ViewModel() {
    private val entities = MutableStateFlow<List<Entity>>(emptyList())
    val visible = MutableStateFlow<List<Entity>>(emptyList())
    val hidden = MutableStateFlow<List<Entity>>(emptyList())
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.observeAll().catch { _messages.emit(it.toUserMessage()) }.collect { list ->
                entities.value = list
                visible.value = EntitySuggestions.selectable(list).sortedBy { it.name }
                hidden.value = list.filter { it.hidden }.sortedBy { it.name }
            }
        }
    }

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { repo.setHidden(id, hidden).onFailure { _messages.emit(it.toUserMessage()) } }
    }
}

data class EntityEditState(
    val name: String = "",
    val type: EntityType = EntityType.PERSON,
    val hidden: Boolean = false,
    val memberIds: List<String> = emptyList(),
    val all: List<Entity> = emptyList(),
    val savedId: String? = null,
)

class EntityEditViewModel(
    entityId: String,
    private val repo: EntityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EntityEditState(savedId = entityId.takeUnless { it == NEW }))
    val state = _state
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var loaded = entityId == NEW

    init {
        viewModelScope.launch {
            repo.observeAll().catch { _messages.emit(it.toUserMessage()) }.collect { list ->
                val current = list.find { it.id == (_state.value.savedId ?: entityId) }
                if (current != null && !loaded) {
                    loaded = true
                    _state.update {
                        it.copy(
                            name = current.name,
                            type = current.type,
                            hidden = current.hidden,
                            memberIds = current.memberIds,
                            savedId = current.id,
                        )
                    }
                }
                _state.update { state ->
                    val fresh = list.find { it.id == state.savedId }
                    state.copy(all = list, memberIds = fresh?.memberIds ?: state.memberIds)
                }
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value) }
    fun onType(value: EntityType) = _state.update { it.copy(type = value) }
    fun onHidden(value: Boolean) {
        _state.update { it.copy(hidden = value) }
        val id = _state.value.savedId ?: return
        viewModelScope.launch { repo.setHidden(id, value).onFailure { _messages.emit(it.toUserMessage()) } }
    }

    fun setMembers(ids: List<String>) {
        val id = _state.value.savedId ?: return
        _state.update { it.copy(memberIds = ids) }
        viewModelScope.launch { repo.setMembers(id, ids).onFailure { _messages.emit(it.toUserMessage()) } }
    }

    fun createMember(name: String) {
        val forumId = _state.value.savedId ?: return
        viewModelScope.launch {
            repo.create(name, EntityType.PERSON).onSuccess { person ->
                setMembers(_state.value.memberIds + person.id)
            }.onFailure { _messages.emit(it.toUserMessage()) }
        }
    }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _messages.tryEmit(UserMessage.NEED_DESCRIPTION)
            return
        }
        viewModelScope.launch {
            val id = current.savedId
            if (id == null) {
                repo.create(current.name, current.type).onSuccess { created ->
                    _state.update { it.copy(savedId = created.id) }
                    if (current.hidden) repo.setHidden(created.id, true)
                    _messages.emit(UserMessage.CLOTHING_SAVED)
                }.onFailure { _messages.emit(it.toUserMessage()) }
            } else {
                repo.update(id, current.name, current.type).onFailure { _messages.emit(it.toUserMessage()) }
                    .onSuccess { _messages.emit(UserMessage.CLOTHING_SAVED) }
            }
        }
    }

    companion object {
        const val NEW = "new"
    }
}
