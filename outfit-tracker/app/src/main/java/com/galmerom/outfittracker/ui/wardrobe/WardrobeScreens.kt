package com.galmerom.outfittracker.ui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.common.CategoryDropdown
import com.galmerom.outfittracker.ui.common.ClothingImage
import com.galmerom.outfittracker.ui.common.EmptyState
import com.galmerom.outfittracker.ui.common.SectionTitle
import com.galmerom.outfittracker.ui.common.appCardColors
import com.galmerom.outfittracker.ui.common.appCardElevation
import com.galmerom.outfittracker.ui.common.appTextFieldColors
import com.galmerom.outfittracker.ui.common.labelRes
import com.galmerom.outfittracker.ui.common.rememberPhotoActions
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.viewModelFactory

@Composable
fun WardrobeScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, onHidden: () -> Unit) {
    val container = LocalAppContainer.current
    val clothing = container.clothingRepository ?: return
    val categoriesRepo = container.categoryRepository ?: return
    val viewModel: WardrobeViewModel = viewModel(
        factory = viewModelFactory { WardrobeViewModel(clothing, container.backgroundRemover) },
    )
    val items by viewModel.visible.collectAsStateWithLifecycle()
    val improve by viewModel.improve.collectAsStateWithLifecycle()
    val categories by categoriesRepo.observeAll().collectAsStateWithLifecycle(emptyList())
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) }
    }
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
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.wardrobe_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (improve.running) {
                            Text(
                                stringResource(R.string.improve_progress, improve.done, improve.total),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        } else {
                            TextButton(onClick = viewModel::improvePhotos) {
                                Text(stringResource(R.string.improve_photos), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        TextButton(onClick = onHidden) {
                            Text(stringResource(R.string.wardrobe_hidden))
                        }
                    }
                    if (improve.running) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                    }
                    if (improve.finished) {
                        Text(stringResource(R.string.improve_done, improve.updated))
                    }
                    if (improve.empty) {
                        Text(stringResource(R.string.improve_none))
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    EmptyState(message = stringResource(R.string.wardrobe_empty)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_hanger),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            categories.forEach { category ->
                val group = items.filter { it.categoryId == category.id }
                if (group.isEmpty()) return@forEach
                item(key = "header-${category.id}") {
                    SectionTitle(category.name)
                }
                items(group, key = { it.id }) { clothingItem ->
                    ClothingRow(clothingItem, onClick = { onEdit(clothingItem.id) })
                }
            }
        }
    }
}

@Composable
fun HiddenItemsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val clothing = container.clothingRepository ?: return
    val categoriesRepo = container.categoryRepository ?: return
    val viewModel: WardrobeViewModel = viewModel(
        factory = viewModelFactory { WardrobeViewModel(clothing, container.backgroundRemover) },
    )
    val categories by categoriesRepo.observeAll().collectAsStateWithLifecycle(emptyList())
    val items by viewModel.hidden.collectAsStateWithLifecycle()
    SecondaryPage(title = stringResource(R.string.wardrobe_hidden), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(message = stringResource(R.string.wardrobe_hidden_empty)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_hanger),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = appCardColors(),
                    elevation = appCardElevation(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ClothingImage(
                            jpeg = null,
                            url = item.shownPhotoUrl(),
                            description = item.description,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.description, style = MaterialTheme.typography.titleMedium)
                            Text(categories.firstOrNull { it.id == item.categoryId }?.name ?: item.categoryId)
                        }
                        TextButton(onClick = { viewModel.setHidden(item.id, false) }) {
                            Text(stringResource(R.string.clothing_revive))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClothingEditScreen(
    itemId: String,
    initialCategoryId: String? = null,
    onBack: (savedItemId: String?) -> Unit,
) {
    val container = LocalAppContainer.current
    val clothing = container.clothingRepository ?: return
    val categoriesRepo = container.categoryRepository ?: return
    val viewModel: ClothingEditViewModel = viewModel(
        key = "$itemId:${initialCategoryId.orEmpty()}",
        factory = viewModelFactory {
            ClothingEditViewModel(itemId, clothing, categoriesRepo, container.backgroundRemover, initialCategoryId)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(context.textFor(message))
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onBack(state.savedItemId)
    }
    val photos = rememberPhotoActions(
        onJpeg = viewModel::onPhoto,
        onFailure = viewModel::reportGeneric,
        onDenied = viewModel::reportCameraDenied,
    )
    var confirmWithoutPhoto by remember { mutableStateOf(false) }
    val title = if (itemId == ClothingEditViewModel.NEW_ID) {
        stringResource(R.string.clothing_new_title)
    } else {
        stringResource(R.string.clothing_edit_title)
    }
    SecondaryPage(title = title, onBack = { onBack(null) }, snackbar = snackbar) { padding ->
        if (!state.ready) {
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            return@SecondaryPage
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shownUrl = when {
                state.photoJpeg != null -> null
                state.backgroundRemoved -> state.photoUrl
                else -> state.originalPhotoUrl ?: state.photoUrl
            }
            ClothingImage(
                jpeg = state.photoJpeg,
                url = shownUrl,
                description = stringResource(R.string.photo_description),
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            if (state.preparingPhoto) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Text(stringResource(R.string.cutout_working))
                }
            }
            val canToggle = (state.pendingNewPhoto && state.processedJpeg != null) ||
                (!state.pendingNewPhoto && !state.originalPhotoUrl.isNullOrBlank())
            if (canToggle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (state.backgroundRemoved) R.string.cutout_processed else R.string.cutout_original),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.backgroundRemoved, onCheckedChange = viewModel::setBackgroundRemoved)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = photos.onCamera) { Text(stringResource(R.string.clothing_camera)) }
                OutlinedButton(onClick = photos.onGallery) { Text(stringResource(R.string.clothing_gallery)) }
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text(stringResource(R.string.clothing_description)) },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryDropdown(
                categories = state.categories,
                categoryId = state.categoryId,
                onCategory = viewModel::onCategory,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clothing_hidden))
                    Text(
                        stringResource(R.string.clothing_hidden_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.hidden, onCheckedChange = viewModel::onHidden)
            }
            Button(
                onClick = {
                    val hasPhoto = state.photoJpeg != null || !state.photoUrl.isNullOrBlank() || !state.originalPhotoUrl.isNullOrBlank()
                    if (!hasPhoto) {
                        confirmWithoutPhoto = true
                    } else {
                        viewModel.save()
                    }
                },
                enabled = !state.saving && !state.preparingPhoto,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
    if (confirmWithoutPhoto) {
        AlertDialog(
            onDismissRequest = { confirmWithoutPhoto = false },
            title = { Text(stringResource(R.string.clothing_no_photo_title)) },
            text = { Text(stringResource(R.string.clothing_no_photo_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWithoutPhoto = false
                    viewModel.save()
                }) { Text(stringResource(R.string.clothing_no_photo_save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithoutPhoto = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ClothingRow(item: ClothingItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = appCardColors(),
        elevation = appCardElevation(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClothingImage(
                jpeg = null,
                url = item.shownPhotoUrl(),
                description = item.description,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(item.description, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryPage(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}
