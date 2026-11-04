package com.galmerom.outfittracker.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.repository.CategoryRepository
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.common.appCardColors
import com.galmerom.outfittracker.ui.common.appCardElevation
import com.galmerom.outfittracker.ui.common.appTextFieldColors
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.toUserMessage
import com.galmerom.outfittracker.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun CategoryListScreen(onBack: () -> Unit) {
    val repo = LocalAppContainer.current.categoryRepository ?: return
    val viewModel: CategoryListViewModel = viewModel(factory = viewModelFactory { CategoryListViewModel(repo) })
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
            item {
                Text(stringResource(R.string.menu_categories), style = MaterialTheme.typography.titleLarge)
            }
            if (categories.isEmpty()) {
                item { Text(stringResource(R.string.categories_empty)) }
            }
            items(categories, key = { it.id }) { category ->
                val index = categories.indexOfFirst { it.id == category.id }
                CategoryRow(
                    category = category,
                    canMoveUp = index > 0,
                    canMoveDown = index >= 0 && index < categories.lastIndex,
                    onRename = { viewModel.rename(category.id, it) },
                    onMoveUp = { viewModel.move(category.id, -1) },
                    onMoveDown = { viewModel.move(category.id, 1) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.category_new_name)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            viewModel.add(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank(),
                    ) { Text(stringResource(R.string.category_add)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: ClothingCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var name by remember(category.id, category.name) { mutableStateOf(category.name) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = appCardColors(),
        elevation = appCardElevation(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.category_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.category_move_down))
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.clothing_category)) },
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name != category.name) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

class CategoryListViewModel(private val repo: CategoryRepository) : ViewModel() {
    val categories = MutableStateFlow<List<ClothingCategory>>(emptyList())
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var pendingIds: List<String>? = null

    init {
        viewModelScope.launch {
            repo.observeAll().catch { error -> _messages.tryEmit(error.toUserMessage()) }.collect { list ->
                val pending = pendingIds
                if (pending == null || list.map { it.id } == pending) {
                    pendingIds = null
                    categories.value = list
                }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch {
            repo.add(name).onSuccess { _messages.emit(UserMessage.SAVED) }
                .onFailure { _messages.emit(it.toUserMessage()) }
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            repo.rename(id, name).onSuccess { _messages.emit(UserMessage.SAVED) }
                .onFailure { _messages.emit(it.toUserMessage()) }
        }
    }

    fun move(id: String, delta: Int) {
        val list = categories.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        val ids = list.map { it.id }
        pendingIds = ids
        categories.value = list
        viewModelScope.launch {
            repo.reorder(ids).onFailure { error ->
                pendingIds = null
                _messages.emit(error.toUserMessage())
            }
        }
    }
}
