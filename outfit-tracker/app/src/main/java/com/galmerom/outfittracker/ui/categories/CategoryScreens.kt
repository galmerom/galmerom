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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.galmerom.outfittracker.ui.common.appCardColors
import com.galmerom.outfittracker.ui.common.appCardElevation
import com.galmerom.outfittracker.ui.common.appTextFieldColors
import com.galmerom.outfittracker.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun CategoryListScreen(onBack: () -> Unit) {
    val repo = LocalAppContainer.current.categoryRepository ?: return
    val viewModel: CategoryListViewModel = viewModel(factory = viewModelFactory { CategoryListViewModel(repo) })
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
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
                CategoryRow(category, onRename = { viewModel.rename(category.id, it) })
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
private fun CategoryRow(category: ClothingCategory, onRename: (String) -> Unit) {
    var name by remember(category.id, category.name) { mutableStateOf(category.name) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = appCardColors(),
        elevation = appCardElevation(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    init {
        viewModelScope.launch {
            repo.observeAll().catch { }.collect { categories.value = it }
        }
    }

    fun add(name: String) {
        viewModelScope.launch { repo.add(name) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }
}
