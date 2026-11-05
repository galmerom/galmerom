package com.galmerom.quickdial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.galmerom.quickdial.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsPane(
    picker: PickerUi,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = picker.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_contacts)) },
        )
        when {
            picker.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            picker.rows.isEmpty() -> Text(
                stringResource(if (picker.query.isBlank()) R.string.no_phone_contacts else R.string.no_results),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(picker.rows, key = { it.id }) { row ->
                    ContactPickRow(row, onToggle = { onToggle(row.id) }, onRename = { onRename(row.id) })
                }
            }
        }
    }
}

@Composable
private fun ContactPickRow(row: PickerRow, onToggle: () -> Unit, onRename: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(row.displayName, style = MaterialTheme.typography.titleLarge)
            val detail = if (row.selected) {
                stringResource(R.string.selected_number, row.shortName, row.selectedNumber.orEmpty())
            } else {
                row.shortName
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_short_name))
        }
    }
}

@Composable
fun NumberDialog(prompt: NumberPrompt, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selected by remember(prompt.rowId) { mutableIntStateOf(prompt.selectedIndex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_number), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(prompt.displayName, style = MaterialTheme.typography.bodyLarge)
                prompt.options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = { selected = index })
                        Column {
                            Text(option.label, style = MaterialTheme.typography.titleMedium)
                            Text(option.number, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.ok), style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

@Composable
fun RenameDialog(
    prompt: RenamePrompt,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRestorePhoto: () -> Unit,
) {
    var text by remember(prompt.rowId, prompt.initial) { mutableStateOf(prompt.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_short_name), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    label = { Text(stringResource(R.string.short_name)) },
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.photo_section), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGallery, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.photo_gallery), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.photo_camera), style = MaterialTheme.typography.titleMedium)
                }
                if (prompt.hasCustomPhoto) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRestorePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.photo_restore), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
