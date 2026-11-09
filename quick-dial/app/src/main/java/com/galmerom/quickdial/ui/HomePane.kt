package com.galmerom.quickdial.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galmerom.quickdial.R
import com.galmerom.quickdial.domain.avatarIndex
import com.galmerom.quickdial.domain.captionHeightDp
import com.galmerom.quickdial.domain.initialLetter
import com.galmerom.quickdial.domain.letterFontSp
import com.galmerom.quickdial.domain.shortNameFontSp
import com.galmerom.quickdial.domain.tileHeightDp
import com.galmerom.quickdial.ui.theme.AvatarPalette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomePane(
    ui: HomeUi,
    onOpenMenu: () -> Unit,
    onCall: (Tile) -> Unit,
    onWhatsApp: (Tile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (ui.contactsDenied) {
            Text(
                stringResource(R.string.banner_contacts),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (ui.callLogDenied) {
            Text(
                stringResource(R.string.banner_call_log),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            !ui.ready -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.tiles.isEmpty() -> EmptyGrid(onOpenMenu, Modifier.weight(1f))
            else -> ContactGrid(
                tiles = ui.tiles,
                columns = ui.columns,
                tileRatio = ui.tileRatio,
                showBadge = ui.showBadge,
                shortNameSp = ui.shortNameSp,
                onCall = onCall,
                onWhatsApp = onWhatsApp,
                modifier = Modifier.weight(1f).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun EmptyGrid(onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenMenu, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.open_menu), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ContactGrid(
    tiles: List<Tile>,
    columns: Int,
    tileRatio: Float,
    showBadge: Boolean,
    shortNameSp: Int,
    onCall: (Tile) -> Unit,
    onWhatsApp: (Tile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = tiles.chunked(columns)
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { tile ->
                    ContactTile(
                        tile = tile,
                        showBadge = showBadge,
                        shortNameSp = shortNameSp,
                        tileRatio = tileRatio,
                        onCall = { onCall(tile) },
                        onWhatsApp = { onWhatsApp(tile) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ContactTile(
    tile: Tile,
    showBadge: Boolean,
    shortNameSp: Int,
    tileRatio: Float,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val letter = initialLetter(tile.shortName)
    val color = AvatarPalette[avatarIndex(tile.shortName, AvatarPalette.size)]
    val callLabel = stringResource(R.string.tile_call, tile.shortName)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value
        val nameSp = shortNameFontSp(shortNameSp, widthDp)
        val heightDp = tileHeightDp(widthDp, tileRatio, captionHeightDp(nameSp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onCall,
                    onDoubleClick = onWhatsApp,
                    onLongClickLabel = callLabel,
                ),
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                ContactAvatar(
                    photoUri = tile.photoUri,
                    letter = letter,
                    color = color,
                    letterSp = letterFontSp(widthDp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tile.shortName,
                        fontSize = nameSp.sp,
                        lineHeight = (nameSp * 1.15f).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (showBadge) {
                Text(
                    text = if (tile.callCount > 99) "99+" else tile.callCount.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = (nameSp * 0.55f).coerceAtLeast(12f).sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ContactAvatar(
    photoUri: String?,
    letter: String,
    color: Color,
    letterSp: Float,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, photoUri) {
        value = withContext(Dispatchers.IO) {
            if (photoUri.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    val parsed = Uri.parse(photoUri)
                    if (parsed.scheme == "file") {
                        BitmapFactory.decodeFile(parsed.path ?: File(photoUri).path)
                    } else {
                        context.contentResolver.openInputStream(parsed)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, color = Color.White, fontSize = letterSp.sp, lineHeight = letterSp.sp)
            }
        }
    }
}
