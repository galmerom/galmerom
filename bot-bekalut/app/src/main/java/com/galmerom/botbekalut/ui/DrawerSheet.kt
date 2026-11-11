package com.galmerom.botbekalut.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.galmerom.botbekalut.domain.AppSettings
import com.galmerom.botbekalut.domain.FontSizeId
import com.galmerom.botbekalut.domain.ThemeId
import com.galmerom.botbekalut.domain.orderedCatalog
import com.galmerom.botbekalut.domain.parseHexColor
import com.galmerom.botbekalut.ui.theme.rgbColor
import com.galmerom.botbekalut.ui.theme.themeSwatch

@Composable
fun SideMenu(
    settings: AppSettings,
    notificationsGranted: Boolean,
    onTheme: (String) -> Unit,
    onFont: (String) -> Unit,
    onMute: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onPages: () -> Unit,
    onSignOut: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bots = orderedCatalog(settings)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("בוט בקלות", style = MaterialTheme.typography.headlineSmall)
        Text("צבע", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeId.entries.forEach { theme ->
                val selected = settings.themeId == theme.id
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(themeSwatch(theme))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { onTheme(theme.id) },
                    )
                    Text(theme.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("גודל גופן", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        FontSizeId.entries.forEach { size ->
            TextButton(onClick = { onFont(size.id) }) {
                Text(
                    if (settings.fontScale == size.id) "✓  ${size.label}" else size.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (settings.fontScale == size.id) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("התראות", style = MaterialTheme.typography.titleMedium)
        if (!notificationsGranted) {
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                },
            ) {
                Text("התראות כבויות במכשיר")
            }
        }
        bots.forEach { bot ->
            val muted = bot.id in settings.mutedBotIds
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val color = parseHexColor(bot.colorHex)?.let { rgbColor(it) } ?: MaterialTheme.colorScheme.primary
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(bot.name.ifBlank { bot.id }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("השתקה", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = muted, onCheckedChange = { onMute(bot.id, it) })
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("מצב עריכה", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = editing, onCheckedChange = { editing = it })
        }
        Text(
            "סידור כפתורי הבוטים",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            bots.forEachIndexed { index, bot ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(bot.name.ifBlank { bot.id }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onMove(bot.id, -1) }, enabled = index > 0) { Text("למעלה") }
                    TextButton(onClick = { onMove(bot.id, 1) }, enabled = index < bots.lastIndex) { Text("למטה") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextButton(onClick = onPages, modifier = Modifier.fillMaxWidth()) {
            Text("עמודים", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
            Text("יציאה", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("לצאת מהחשבון?") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("יציאה") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("ביטול") }
            },
        )
    }
}
