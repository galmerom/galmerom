package com.galmerom.quickdial.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.galmerom.quickdial.R
import com.galmerom.quickdial.domain.initialLetter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onRemove: () -> Unit,
) {
    var text by remember(prompt.rowId, prompt.initial) { mutableStateOf(prompt.initial) }
    var confirmRemove by remember(prompt.rowId) { mutableStateOf(false) }
    val preview = when {
        !prompt.restoreOriginal && prompt.stagedPhotoUri != null -> prompt.stagedPhotoUri
        prompt.restoreOriginal -> prompt.originalPhotoUri
        else -> prompt.customPhotoUri ?: prompt.originalPhotoUri
    }
    val restoreEnabled = !prompt.restoreOriginal && (prompt.hasCustomPhoto || prompt.stagedPhotoUri != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_short_name), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EditorPhoto(preview, initialLetter(prompt.initial.ifBlank { prompt.displayName }))
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onRestorePhoto,
                    enabled = restoreEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.photo_restore), style = MaterialTheme.typography.titleMedium)
                }
                if (prompt.onGrid) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.remove_from_list), style = MaterialTheme.typography.titleMedium)
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
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_confirm_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    stringResource(R.string.remove_confirm_body, prompt.displayName),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.remove_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EditorPhoto(photoUri: String?, letter: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, photoUri) {
        value = withContext(Dispatchers.IO) {
            if (photoUri.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    val parsed = Uri.parse(photoUri)
                    if (parsed.scheme == "file") {
                        BitmapFactory.decodeFile(parsed.path ?: File(photoUri).path)
                    } else {
                        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(letter, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
