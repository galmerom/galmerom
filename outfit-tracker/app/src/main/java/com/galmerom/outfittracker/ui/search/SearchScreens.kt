package com.galmerom.outfittracker.ui.search

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.common.ClearTextButton
import com.galmerom.outfittracker.ui.common.ClothingImage
import com.galmerom.outfittracker.ui.common.EmptyState
import com.galmerom.outfittracker.ui.common.SectionTitle
import com.galmerom.outfittracker.ui.common.appCardColors
import com.galmerom.outfittracker.ui.common.appCardElevation
import com.galmerom.outfittracker.ui.common.appTextFieldColors
import com.galmerom.outfittracker.ui.common.labelRes
import com.galmerom.outfittracker.ui.common.toHebrewDate
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.viewModelFactory

@Composable
fun SearchScreen(onOpenMeeting: (String) -> Unit, onEditMeeting: (String) -> Unit) {
    val container = LocalAppContainer.current
    val meetings = container.meetingRepository ?: return
    val entities = container.entityRepository ?: return
    val categoriesRepo = container.categoryRepository ?: return
    val viewModel: SearchViewModel = viewModel(factory = viewModelFactory { SearchViewModel(meetings, entities) })
    val categories by categoriesRepo.observeAll().collectAsStateWithLifecycle(emptyList())
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val entityLabel = stringResource(R.string.entity_label)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    label = { Text(stringResource(R.string.search_hint, entityLabel)) },
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        ClearTextButton(state.query.isNotEmpty()) { viewModel.onQuery("") }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.show_hidden_meetings), modifier = Modifier.weight(1f))
                    Switch(checked = state.includeHidden, onCheckedChange = viewModel::setIncludeHidden)
                }
            }
            if (state.visible.isEmpty()) {
                item {
                    EmptyState(message = stringResource(R.string.search_empty)) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            items(state.visible, key = { it.meeting.id }) { hit ->
                val meeting = hit.meeting
                Card(
                    onClick = { onOpenMeeting(meeting.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = appCardColors(),
                    elevation = appCardElevation(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(meeting.date.toHebrewDate(), style = MaterialTheme.typography.titleMedium)
                        Text(meeting.displayNames(), color = MaterialTheme.colorScheme.primary)
                        if (meeting.hidden) {
                            Text(
                                stringResource(R.string.meeting_hidden_badge),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        hit.forumName?.let { forum ->
                            Text(
                                stringResource(R.string.forum_meeting_badge, forum),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (meeting.note.isNotBlank()) {
                            Text(
                                meeting.note,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.hasMore) {
                item {
                    Button(onClick = viewModel::showMore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.show_more))
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(meetingId: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val container = LocalAppContainer.current
    val meetings = container.meetingRepository ?: return
    val clothing = container.clothingRepository ?: return
    val categoriesRepo = container.categoryRepository ?: return
    val categories by categoriesRepo.observeAll().collectAsStateWithLifecycle(emptyList())
    val viewModel: MeetingDetailViewModel = viewModel(
        key = meetingId,
        factory = viewModelFactory { MeetingDetailViewModel(meetingId, meetings, clothing) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(stringResource(R.string.meeting_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val meeting = state.meeting
        if (!state.meetingsLoaded) {
            Text(stringResource(R.string.loading), modifier = Modifier.padding(padding).padding(20.dp))
            return@Scaffold
        }
        if (meeting == null) {
            EmptyState(
                message = stringResource(R.string.meeting_missing),
                modifier = Modifier.padding(padding),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(meeting.date.toHebrewDate(), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.entity_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(meeting.displayNames(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (meeting.hidden) {
                Text(stringResource(R.string.meeting_hidden_badge), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEdit(meeting.id) }) { Text(stringResource(R.string.meeting_edit)) }
                TextButton(onClick = { viewModel.setHidden(!meeting.hidden) }) {
                    Text(
                        stringResource(
                            if (meeting.hidden) R.string.meeting_unhide else R.string.meeting_hide,
                        ),
                    )
                }
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.meeting_delete)) }
            }
            if (!meeting.photoUrl.isNullOrBlank()) {
                ClothingImage(
                    jpeg = null,
                    url = meeting.photoUrl,
                    description = stringResource(R.string.meeting_photo_description),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
            if (meeting.note.isNotBlank()) {
                Text(
                    stringResource(R.string.meeting_note),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(meeting.note)
            }
            SectionTitle(stringResource(R.string.meeting_clothes))
            meeting.clothingItemIds.forEach { id ->
                val item = state.clothes[id]
                if (item == null) {
                    Text(
                        stringResource(R.string.clothing_missing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Card(
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
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.description, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    categories.firstOrNull { it.id == item.categoryId }?.name ?: item.categoryId,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (item.hidden) {
                                    Text(
                                        stringResource(R.string.clothing_hidden_badge),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.meeting_delete_title)) },
                text = { Text(stringResource(R.string.meeting_delete_body)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.delete(onBack) }) {
                        Text(stringResource(R.string.meeting_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}
