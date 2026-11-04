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
import androidx.compose.material3.Button
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
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.common.ClothingImage
import com.galmerom.outfittracker.ui.common.labelRes
import com.galmerom.outfittracker.ui.common.toHebrewDate
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.viewModelFactory

@Composable
fun SearchScreen(onOpenMeeting: (String) -> Unit) {
    val meetings = LocalAppContainer.current.meetingRepository ?: return
    val viewModel: SearchViewModel = viewModel(factory = viewModelFactory { SearchViewModel(meetings) })
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    label = { Text(stringResource(R.string.search_hint, entityLabel)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.visible.isEmpty()) {
                item { Text(stringResource(R.string.search_empty)) }
            }
            items(state.visible, key = { it.id }) { meeting ->
                Card(onClick = { onOpenMeeting(meeting.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(meeting.date.toHebrewDate(), style = MaterialTheme.typography.titleMedium)
                        Text(meeting.entityName)
                        if (meeting.note.isNotBlank()) {
                            Text(meeting.note, maxLines = 2, style = MaterialTheme.typography.bodySmall)
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
fun MeetingDetailScreen(meetingId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val meetings = container.meetingRepository ?: return
    val clothing = container.clothingRepository ?: return
    val viewModel: MeetingDetailViewModel = viewModel(
        key = meetingId,
        factory = viewModelFactory { MeetingDetailViewModel(meetingId, meetings, clothing) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(context.textFor(it)) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
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
            Text(stringResource(R.string.loading), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        if (meeting == null) {
            Text(stringResource(R.string.meeting_missing), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(meeting.date.toHebrewDate(), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.entity_label), style = MaterialTheme.typography.labelLarge)
            Text(meeting.entityName, style = MaterialTheme.typography.titleMedium)
            if (!meeting.photoUrl.isNullOrBlank()) {
                ClothingImage(
                    jpeg = null,
                    url = meeting.photoUrl,
                    description = stringResource(R.string.meeting_photo_description),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
            if (meeting.note.isNotBlank()) {
                Text(stringResource(R.string.meeting_note), style = MaterialTheme.typography.labelLarge)
                Text(meeting.note)
            }
            Text(stringResource(R.string.meeting_clothes), style = MaterialTheme.typography.titleMedium)
            meeting.clothingItemIds.forEach { id ->
                val item = state.clothes[id]
                if (item == null) {
                    Text(stringResource(R.string.clothing_missing))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClothingImage(
                            jpeg = null,
                            url = item.photoUrl,
                            description = item.description,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(item.description, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(item.category.labelRes()))
                            if (item.hidden) {
                                Text(stringResource(R.string.clothing_hidden_badge))
                            }
                        }
                    }
                }
            }
        }
    }
}
