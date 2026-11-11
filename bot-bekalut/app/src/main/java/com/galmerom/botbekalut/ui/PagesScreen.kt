package com.galmerom.botbekalut.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galmerom.botbekalut.domain.PageEntry
import com.galmerom.botbekalut.domain.groupPages
import com.galmerom.botbekalut.ui.theme.rgbColor

@Composable
fun PagesScreen(
    pages: List<PageEntry>,
    hidden: Set<String>,
    order: List<String>,
    onBack: () -> Unit,
    onHidden: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val groups = groupPages(pages, hidden, showHidden, order)
    val context = LocalContext.current
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
            Text("עמודים", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text("הצג מוסתרים", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = showHidden, onCheckedChange = { showHidden = it })
        }
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (pages.isEmpty()) "אין עמודים" else "כל העמודים מוסתרים",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { group ->
                    item(key = "group-${group.botId}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Box(
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(rgbColor(group.colorRgb)),
                            )
                            Text(group.botName, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    items(group.pages, key = { it.canvasId }) { page ->
                        val hiddenNow = page.canvasId in hidden
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openPageUrl(context, page.url) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(page.title.ifBlank { page.canvasId }, style = MaterialTheme.typography.titleMedium)
                            if (page.description.isNotBlank()) {
                                Text(
                                    page.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onHidden(page.canvasId, !hiddenNow) }) {
                                Text(if (hiddenNow) "ביטול הסתרה" else "הסתרה")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
