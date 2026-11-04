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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.model.ClothingCategory
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
    val clothing = LocalAppContainer.current.clothingRepository ?: return
    val viewModel: WardrobeViewModel = viewModel(factory = viewModelFactory { WardrobeViewModel(clothing) })
    val items by viewModel.visible.collectAsStateWithLifecycle()
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
                TextButton(onClick = onHidden) {
                    Text(stringResource(R.string.wardrobe_hidden))
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
            ClothingCategory.entries.forEach { category ->
                val group = items.filter { it.category == category }
                if (group.isEmpty()) return@forEach
                item(key = "header-$category") {
                    SectionTitle(stringResource(category.labelRes()))
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
    val clothing = LocalAppContainer.current.clothingRepository ?: return
    val viewModel: WardrobeViewModel = viewModel(factory = viewModelFactory { WardrobeViewModel(clothing) })
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
                            url = item.photoUrl,
                            description = item.description,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.description, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(item.category.labelRes()))
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
fun ClothingEditScreen(itemId: String, onBack: () -> Unit) {
    val clothing = LocalAppContainer.current.clothingRepository ?: return
    val viewModel: ClothingEditViewModel = viewModel(
        key = itemId,
        factory = viewModelFactory { ClothingEditViewModel(itemId, clothing) },
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
        if (state.saved) onBack()
    }
    val photos = rememberPhotoActions(
        onJpeg = viewModel::onPhoto,
        onFailure = viewModel::reportGeneric,
        onDenied = viewModel::reportCameraDenied,
    )
    val title = if (itemId == ClothingEditViewModel.NEW_ID) {
        stringResource(R.string.clothing_new_title)
    } else {
        stringResource(R.string.clothing_edit_title)
    }
    SecondaryPage(title = title, onBack = onBack, snackbar = snackbar) { padding ->
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
            ClothingImage(
                jpeg = state.photoJpeg,
                url = state.photoUrl,
                description = stringResource(R.string.photo_description),
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
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
                category = state.category,
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
                onClick = viewModel::save,
                enabled = !state.saving,
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
                url = item.photoUrl,
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(title) },
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
