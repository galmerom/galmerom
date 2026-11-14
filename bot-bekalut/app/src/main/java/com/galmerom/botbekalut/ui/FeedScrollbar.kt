package com.galmerom.botbekalut.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.galmerom.botbekalut.domain.feedScrollProgress
import com.galmerom.botbekalut.domain.feedScrollTarget
import com.galmerom.botbekalut.domain.feedThumbLength
import kotlinx.coroutines.launch

/**
 * Thin scrollbar on the physical LEFT edge of the feed (the app is right-to-left, so the layout direction is
 * forced to LTR inside). It shows where the user is and can be dragged (or touched) to jump through the feed.
 * The touch strip is 24dp wide; the drawn track and thumb are 5dp. Hidden when the list does not scroll.
 * Place it inside the same Box as the LazyColumn; it fills that Box but only the strip takes touches.
 */
@Composable
fun FeedScrollbar(listState: LazyListState) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val padPx = with(density) { 8.dp.toPx() }
    val minThumbPx = with(density) { 40.dp.toPx() }
    val barPx = with(density) { 5.dp.toPx() }
    val edgePx = with(density) { 4.dp.toPx() }
    var heightPx by remember { mutableIntStateOf(0) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var dragVisible by remember { mutableIntStateOf(1) }
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val scrollable = listState.canScrollForward || listState.canScrollBackward
    if (!scrollable && dragProgress < 0f) return

    fun moveTo(y: Float) {
        val total = listState.layoutInfo.totalItemsCount
        if (total <= 0) return
        val track = (heightPx - 2 * padPx).coerceAtLeast(1f)
        val thumb = feedThumbLength(total, dragVisible, track, minThumbPx)
        val progress = ((y - padPx - thumb / 2f) / (track - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
        dragProgress = progress
        val target = feedScrollTarget(progress, total, dragVisible)
        val items = listState.layoutInfo.visibleItemsInfo
        val average = if (items.isEmpty()) 0 else items.sumOf { it.size } / items.size
        scope.launch { listState.scrollToItem(target.index, (target.fraction * average).toInt()) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .onSizeChanged { heightPx = it.height }
                    .pointerInput(listState) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragVisible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                moveTo(offset.y)
                            },
                            onDragEnd = { dragProgress = -1f },
                            onDragCancel = { dragProgress = -1f },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                moveTo(change.position.y)
                            },
                        )
                    }
                    .drawBehind {
                        val info = listState.layoutInfo
                        val total = info.totalItemsCount
                        val items = info.visibleItemsInfo
                        val trackH = size.height - 2 * padPx
                        if (total <= 0 || items.isEmpty() || trackH <= 0f) return@drawBehind
                        val dragging = dragProgress >= 0f
                        val first = items.firstOrNull { it.index == listState.firstVisibleItemIndex } ?: items.first()
                        val fraction = listState.firstVisibleItemScrollOffset.toFloat() / first.size.coerceAtLeast(1)
                        val live = feedScrollProgress(
                            totalItems = total,
                            visibleItems = items.size,
                            firstIndex = listState.firstVisibleItemIndex,
                            firstFraction = fraction,
                            atEnd = !listState.canScrollForward,
                        )
                        val progress = if (dragging) dragProgress else live
                        val visible = if (dragging) dragVisible else items.size
                        val thumb = feedThumbLength(total, visible, trackH, minThumbPx)
                        val radius = CornerRadius(barPx / 2f, barPx / 2f)
                        drawRoundRect(
                            color = thumbColor.copy(alpha = 0.08f),
                            topLeft = Offset(edgePx, padPx),
                            size = Size(barPx, trackH),
                            cornerRadius = radius,
                        )
                        drawRoundRect(
                            color = thumbColor.copy(alpha = if (dragging) 0.6f else 0.4f),
                            topLeft = Offset(edgePx, padPx + progress * (trackH - thumb)),
                            size = Size(barPx, thumb),
                            cornerRadius = radius,
                        )
                    },
            )
        }
    }
}
