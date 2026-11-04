package com.galmerom.outfittracker.ui.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.logic.Dates
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.common.CategoryDropdown
import com.galmerom.outfittracker.ui.common.ClearTextButton
import com.galmerom.outfittracker.ui.common.ClothingImage
import com.galmerom.outfittracker.ui.common.ClothingItemMenu
import com.galmerom.outfittracker.ui.common.SectionTitle
import com.galmerom.outfittracker.ui.common.appCardColors
import com.galmerom.outfittracker.ui.common.appCardElevation
import com.galmerom.outfittracker.ui.common.appTextFieldColors
import com.galmerom.outfittracker.ui.common.labelRes
import com.galmerom.outfittracker.ui.common.rememberPhotoActions
import com.galmerom.outfittracker.ui.common.toHebrewDate
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogMeetingScreen() {
    val container = LocalAppContainer.current
    val clothing = container.clothingRepository ?: return
    val entities = container.entityRepository ?: return
    val meetings = container.meetingRepository ?: return
    val categoriesRepo = container.categoryRepository ?: return
    val detector = container.detector ?: return
    val viewModel: LogMeetingViewModel = viewModel(
        factory = viewModelFactory { LogMeetingViewModel(clothing, entities, meetings, categoriesRepo, detector) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDate by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) }
    }
    val entityLabel = stringResource(R.string.entity_label)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = appCardColors(),
                elevation = appCardElevation(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.date.toHebrewDate(), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { showDate = true }) {
                            Text(stringResource(R.string.log_pick_date))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.entityQuery,
                onValueChange = viewModel::onQuery,
                label = { Text(stringResource(R.string.entity_search_label, entityLabel)) },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    ClearTextButton(state.entityQuery.isNotEmpty()) { viewModel.onQuery("") }
                },
            )
            state.selectedEntity?.let { selected ->
                Text(
                    stringResource(R.string.entity_selected, selected.name),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = viewModel::clearEntity) {
                    Text(stringResource(R.string.entity_clear))
                }
            }
            state.suggestions.forEach { entity ->
                Card(
                    onClick = { viewModel.selectEntity(entity) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = appCardColors(),
                    elevation = appCardElevation(),
                ) {
                    Text(
                        text = "${entity.name} · ${stringResource(entity.type.labelRes())}",
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            if (state.offerCreate) {
                Text(stringResource(R.string.entity_type))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.newEntityType == EntityType.PERSON,
                        onClick = { viewModel.onEntityType(EntityType.PERSON) },
                        label = { Text(stringResource(R.string.entity_type_person)) },
                    )
                    FilterChip(
                        selected = state.newEntityType == EntityType.FORUM,
                        onClick = { viewModel.onEntityType(EntityType.FORUM) },
                        label = { Text(stringResource(R.string.entity_type_forum)) },
                    )
                }
                Button(
                    onClick = viewModel::createEntity,
                    enabled = !state.creatingEntity,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.entity_create, entityLabel, state.entityQuery.trim()))
                }
            }

            SectionTitle(stringResource(R.string.ai_section))
            Text(
                stringResource(R.string.ai_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DetectionBlock(
                jpeg = state.detectionPhoto,
                detecting = state.detecting,
                onPhoto = viewModel::onDetectionPhoto,
                onDetect = viewModel::detect,
                onDenied = { viewModel.notify(UserMessage.CAMERA_PERMISSION) },
                onFailure = { viewModel.notify(UserMessage.GENERIC) },
            )
            state.aiSuggestions.forEachIndexed { index, suggestion ->
                val categoryName = state.categories.firstOrNull { it.id == suggestion.garment.categoryId }?.name
                    ?: suggestion.garment.categoryId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = appCardColors(),
                    elevation = appCardElevation(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(suggestion.garment.description)
                        Text(categoryName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val match = suggestion.match
                        Text(
                            if (match != null) {
                                stringResource(R.string.ai_match, match.description)
                            } else {
                                stringResource(R.string.ai_new)
                            },
                        )
                        TextButton(onClick = { viewModel.acceptSuggestion(index) }) {
                            Text(stringResource(R.string.ai_use))
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.clothes_section))
            state.rows.forEach { row ->
                ClothingPickRowCard(state = state, row = row, viewModel = viewModel)
            }
            OutlinedButton(onClick = viewModel::addClothingRow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.clothes_add_row))
            }
            Text(stringResource(R.string.clothes_selected), style = MaterialTheme.typography.titleSmall)
            val selected = state.wardrobe.filter { it.id in state.selectedClothingIds }
            if (selected.isEmpty()) {
                Text(
                    stringResource(R.string.clothes_none_selected),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selected.forEach { item ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeClothing(item.id) },
                            label = { Text(item.description) },
                        )
                    }
                }
            }
            TextButton(onClick = { showManual = true }) {
                Text(stringResource(R.string.clothes_manual_add))
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.note_label)) },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text(stringResource(R.string.meeting_photo), style = MaterialTheme.typography.titleSmall)
            PhotoBlock(
                jpeg = state.meetingPhoto,
                description = stringResource(R.string.meeting_photo_description),
                onPhoto = viewModel::onMeetingPhoto,
                onDenied = { viewModel.notify(UserMessage.CAMERA_PERMISSION) },
                onFailure = { viewModel.notify(UserMessage.GENERIC) },
            )
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.meeting_save))
                }
            }
        }
        SnackbarHost(snackbar)
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = Dates.toEpochMillis(state.date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { viewModel.onDate(Dates.fromEpochMillis(it)) }
                        showDate = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (showManual) {
        ManualItemDialog(
            categories = state.categories,
            onDismiss = { showManual = false },
            onDenied = { viewModel.notify(UserMessage.CAMERA_PERMISSION) },
            onFailure = { viewModel.notify(UserMessage.GENERIC) },
            onSave = { description, categoryId, jpeg ->
                viewModel.addManual(description, categoryId, jpeg)
                showManual = false
            },
        )
    }
}

@Composable
private fun ClothingPickRowCard(
    state: LogMeetingUiState,
    row: ClothingPickRow,
    viewModel: LogMeetingViewModel,
) {
    val inCategory = state.selectable.filter { row.categoryId.isBlank() || it.categoryId == row.categoryId }
    val hits = if (row.query.isBlank()) {
        emptyList()
    } else {
        inCategory.filter { it.description.contains(row.query.trim(), ignoreCase = true) }
    }
    val chosen = state.wardrobe.firstOrNull { it.id == row.itemId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = appCardColors(),
        elevation = appCardElevation(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryDropdown(
                categories = state.categories,
                categoryId = row.categoryId,
                onCategory = { viewModel.onRowCategory(row.key, it) },
            )
            ClothingItemMenu(
                label = chosen?.description ?: stringResource(R.string.clothes_pick_item),
                items = inCategory,
                onPick = { viewModel.selectRowItem(row.key, it) },
            )
            OutlinedTextField(
                value = row.query,
                onValueChange = { viewModel.onRowQuery(row.key, it) },
                label = { Text(stringResource(R.string.clothes_search)) },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    ClearTextButton(row.query.isNotEmpty()) { viewModel.onRowQuery(row.key, "") }
                },
            )
            hits.forEach { item ->
                Card(onClick = { viewModel.selectRowItem(row.key, item) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClothingImage(null, item.photoUrl, item.description, Modifier.size(40.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(item.description)
                    }
                }
            }
            TextButton(onClick = { viewModel.removeClothingRow(row.key) }) {
                Text(stringResource(R.string.remove_row))
            }
        }
    }
}

@Composable
private fun DetectionBlock(
    jpeg: ByteArray?,
    detecting: Boolean,
    onPhoto: (ByteArray) -> Unit,
    onDetect: () -> Unit,
    onDenied: () -> Unit,
    onFailure: () -> Unit,
) {
    PhotoBlock(
        jpeg = jpeg,
        description = stringResource(R.string.ai_section),
        onPhoto = onPhoto,
        onDenied = onDenied,
        onFailure = onFailure,
    )
    Button(onClick = onDetect, enabled = jpeg != null && !detecting) {
        if (detecting) {
            Text(stringResource(R.string.ai_detecting))
        } else {
            Text(stringResource(R.string.ai_detect))
        }
    }
}

@Composable
private fun PhotoBlock(
    jpeg: ByteArray?,
    description: String,
    onPhoto: (ByteArray) -> Unit,
    onDenied: () -> Unit,
    onFailure: () -> Unit,
) {
    val actions = rememberPhotoActions(onJpeg = onPhoto, onFailure = onFailure, onDenied = onDenied)
    if (jpeg != null) {
        ClothingImage(
            jpeg = jpeg,
            url = null,
            description = description,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onCamera) { Text(stringResource(R.string.clothing_camera)) }
        OutlinedButton(onClick = actions.onGallery) { Text(stringResource(R.string.clothing_gallery)) }
    }
}

@Composable
private fun ManualItemDialog(
    onDismiss: () -> Unit,
    onDenied: () -> Unit,
    onFailure: () -> Unit,
    categories: List<com.galmerom.outfittracker.domain.model.ClothingCategory>,
    onSave: (String, String, ByteArray?) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var categoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clothes_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.clothing_description)) },
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.extraSmall,
                )
                CategoryDropdown(
                    categories = categories,
                    categoryId = categoryId,
                    onCategory = { categoryId = it },
                )
                PhotoBlock(
                    jpeg = photo,
                    description = stringResource(R.string.photo_description),
                    onPhoto = { photo = it },
                    onDenied = onDenied,
                    onFailure = onFailure,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(description, categoryId, photo) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
