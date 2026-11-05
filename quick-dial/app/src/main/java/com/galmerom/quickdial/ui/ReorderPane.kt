package com.galmerom.quickdial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galmerom.quickdial.R
import com.galmerom.quickdial.domain.Slide

@Suppress("DEPRECATION") // Absolute left/right. AutoMirrored would flip these arrows under RTL.
@Composable
fun ReorderPane(tiles: List<Tile>, onSlide: (String, Slide) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.reorder_help),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tiles, key = { _, tile -> tile.id }) { index, tile ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onSlide(tile.id, Slide.Right) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.move_right))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tile.shortName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            tile.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onSlide(tile.id, Slide.Left) },
                        enabled = index < tiles.lastIndex,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.move_left))
                    }
                }
            }
        }
    }
}
