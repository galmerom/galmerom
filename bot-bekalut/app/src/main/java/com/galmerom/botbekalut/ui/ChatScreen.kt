@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.galmerom.botbekalut.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.galmerom.botbekalut.ScrollCommand
import com.galmerom.botbekalut.UiState
import com.galmerom.botbekalut.domain.Block
import com.galmerom.botbekalut.domain.BotButton
import com.galmerom.botbekalut.domain.ChatMessage
import com.galmerom.botbekalut.domain.ChatRow
import com.galmerom.botbekalut.domain.buildChatRows
import com.galmerom.botbekalut.domain.messageIdNear
import com.galmerom.botbekalut.domain.messagePlainText
import com.galmerom.botbekalut.domain.parseMarkdown
import com.galmerom.botbekalut.domain.visibleBots
import com.galmerom.botbekalut.domain.visibleMessages
import com.galmerom.botbekalut.domain.formatBroadcastStamp
import com.galmerom.botbekalut.ui.theme.rgbColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data object CopyAllKey

@Composable
fun ChatPane(
    state: UiState,
    scroll: ScrollCommand?,
    onOpenDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onBotSettled: (String) -> Unit,
    onRevealUnread: (String) -> Unit,
    onSaveAnchor: (String, String, Int) -> Unit,
    onMarkRead: (String) -> Unit,
    onClear: (String) -> Unit,
) {
    val shownMessages = remember(state.messages, state.settings.clearedBefore) {
        visibleMessages(state.messages, state.settings.clearedBefore)
    }
    val bots = visibleBots(
        messages = shownMessages,
        order = state.settings.botOrder,
        readCursors = state.settings.readCursors,
        now = System.currentTimeMillis(),
    )
    val activeId = state.activeBotId ?: bots.firstOrNull()?.id
    if (bots.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            BotTopBar(
                bots = emptyList(),
                activeId = null,
                refreshing = state.refreshing,
                onOpenDrawer = onOpenDrawer,
                onRefresh = onRefresh,
                onSelect = {},
            )
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "אין הודעות ב-24 השעות האחרונות",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
        return
    }
    val initial = bots.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { bots.size })
    val scope = rememberCoroutineScope()
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    LaunchedEffect(activeId, bots.joinToString { it.id }) {
        val index = bots.indexOfFirst { it.id == activeId }
        if (index >= 0 && pagerState.currentPage != index && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(index)
        }
    }
    LaunchedEffect(pagerState.settledPage, bots.joinToString { it.id }) {
        bots.getOrNull(pagerState.settledPage)?.let { onBotSettled(it.id) }
    }
    val current = bots.firstOrNull { it.id == activeId }
    Column(Modifier.fillMaxSize()) {
        BotTopBar(
            bots = bots,
            activeId = activeId,
            refreshing = state.refreshing,
            onOpenDrawer = onOpenDrawer,
            onRefresh = onRefresh,
            onSelect = { id ->
                val index = bots.indexOfFirst { it.id == id }
                if (id == activeId && current?.hasUnread == true) {
                    onRevealUnread(id)
                } else if (index >= 0) {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            },
        )
        if (activeId != null) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                if (current?.hasUnread == true) {
                    TextButton(onClick = { onMarkRead(activeId) }) {
                        Text("סמן כנקרא", style = MaterialTheme.typography.titleMedium)
                    }
                }
                TextButton(onClick = { onClear(activeId) }) {
                    Text("נקה", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val bot = bots[page]
                BotMessageList(
                    botId = bot.id,
                    messages = shownMessages.filter { it.botId == bot.id }.sortedBy { it.publishedAt },
                    listState = listStates.getOrPut(bot.id) { LazyListState() },
                    scroll = scroll,
                    onSaveAnchor = onSaveAnchor,
                    onMarkRead = onMarkRead,
                )
            }
        }
    }
}

@Composable
private fun BotTopBar(
    bots: List<BotButton>,
    activeId: String?,
    refreshing: Boolean,
    onOpenDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Filled.Menu, contentDescription = "תפריט")
        }
        IconButton(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            if (refreshing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "רענון")
            }
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(bots, key = { it.id }) { bot ->
                BotChip(bot = bot, selected = bot.id == activeId, onClick = { onSelect(bot.id) })
            }
        }
    }
}

@Composable
private fun BotChip(bot: BotButton, selected: Boolean, onClick: () -> Unit) {
    val color = rgbColor(bot.colorRgb)
    Box {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            shape = CircleShape,
            border = if (selected) BorderStroke(2.dp, Color.White) else null,
        ) {
            Text(bot.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
        }
        if (bot.hasUnread) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun BotMessageList(
    botId: String,
    messages: List<ChatMessage>,
    listState: LazyListState,
    scroll: ScrollCommand?,
    onSaveAnchor: (String, String, Int) -> Unit,
    onMarkRead: (String) -> Unit,
) {
    val rows = remember(messages) { buildChatRows(messages) }
    var appliedNonce by remember(botId) { mutableLongStateOf(0L) }
    var saveEnabled by remember(botId) { mutableStateOf(false) }
    var highlightId by remember(botId) { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf<Pair<String, String>?>(null) }
    var table by remember { mutableStateOf<Block.Table?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            delay(1_500)
            highlightId = null
        }
    }
    LaunchedEffect(scroll?.nonce, rows.size) {
        val command = scroll ?: return@LaunchedEffect
        if (command.botId != botId || rows.isEmpty() || appliedNonce == command.nonce) return@LaunchedEffect
        val index = if (command.messageId == null) {
            rows.lastIndex
        } else {
            rows.indexOfFirst { it is ChatRow.Message && it.message.id == command.messageId }
                .takeIf { it >= 0 } ?: rows.lastIndex
        }
        listState.scrollToItem(index.coerceAtLeast(0), command.offset.coerceAtLeast(0))
        appliedNonce = command.nonce
        saveEnabled = true
        if (command.highlight) highlightId = command.messageId
    }
    LaunchedEffect(saveEnabled, botId, rows) {
        if (!saveEnabled) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(700)
            .collect { (index, offset) ->
                val messageId = messageIdNear(rows, index) ?: return@collect
                onSaveAnchor(botId, messageId, offset)
            }
    }
    LaunchedEffect(saveEnabled, messages.lastOrNull()?.id) {
        if (!saveEnabled) return@LaunchedEffect
        snapshotFlow { messages.isNotEmpty() && !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { atEnd -> if (atEnd) onMarkRead(botId) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            rows.forEach { row ->
                when (row) {
                    is ChatRow.Day -> item(key = "day-${row.key}") {
                        Text(
                            row.label,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    is ChatRow.Routine -> stickyHeader(key = row.key) {
                        Text(
                            row.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ChatRow.Message -> item(key = row.message.id) {
                        MessageBubble(
                            message = row.message,
                            highlight = highlightId == row.message.id,
                            onImage = { alt, url -> zoom = alt to url },
                            onTable = { table = it },
                        )
                    }
                }
            }
        }
        FeedScrollbar(listState)
        AnimatedVisibility(
            visible = saveEnabled && listState.canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) {
            FilledTonalIconButton(
                onClick = { scope.launch { listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0)) } },
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "להודעה האחרונה")
            }
        }
    }
    zoom?.let { (alt, url) ->
        Dialog(onDismissRequest = { zoom = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ChatImage(
                    url = url,
                    alt = alt,
                    onClick = { zoom = null },
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                TextButton(onClick = { zoom = null }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text("סגירה", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    table?.let { FullscreenTable(table = it, onDismiss = { table = null }) }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    highlight: Boolean,
    onImage: (String, String) -> Unit,
    onTable: (Block.Table) -> Unit,
) {
    val context = LocalContext.current
    val background by animateColorAsState(
        if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(400),
        label = "highlight",
    )
    val plain = remember(message) { messagePlainText(message) }
    val inlineImages = remember(message.content) {
        parseMarkdown(message.content).filterIsInstance<Block.Image>().map { it.url }.toSet()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background),
    ) {
        HorizontalDivider(
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Text(
            formatBroadcastStamp(message.publishedAt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
        )
        SelectionContainer(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).appendTextContextMenuComponents {
                separator()
                item(key = CopyAllKey, label = "העתק הכל") {
                    copyPlainText(context, plain)
                    close()
                }
            },
        ) {
            Column {
                MarkdownBlocks(markdown = message.content, onImage = onImage, onTable = onTable)
                message.images.filter { it.url.isNotBlank() && it.url !in inlineImages }.forEach { image ->
                    ChatImage(url = image.url, alt = image.alt, onClick = { onImage(image.alt, image.url) })
                }
            }
        }
    }
}
