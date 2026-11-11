package com.galmerom.botbekalut.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.galmerom.botbekalut.domain.Block
import com.galmerom.botbekalut.domain.BlockDirection
import com.galmerom.botbekalut.domain.Inline
import com.galmerom.botbekalut.domain.blockDirection
import com.galmerom.botbekalut.domain.inlineDirectionSource
import com.galmerom.botbekalut.domain.ltrIsolate
import com.galmerom.botbekalut.domain.parseMarkdown
import com.galmerom.botbekalut.domain.splitInlineLines

@Composable
fun MarkdownBlocks(
    markdown: String,
    onImage: (alt: String, url: String) -> Unit,
    onTable: (Block.Table) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph -> DirectedLines(
                    block.inlines,
                    MaterialTheme.typography.bodyLarge,
                    linkColor,
                    context,
                )
                is Block.Heading -> DirectedLines(
                    block.inlines,
                    when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    linkColor,
                    context,
                )
                is Block.Quote -> Column(Modifier.padding(vertical = 4.dp)) {
                    splitInlineLines(block.inlines).forEach { line ->
                        DirectedRow(blockDirection(inlineDirectionSource(line))) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                            ) {
                                Box(
                                    Modifier
                                        .padding(end = 10.dp)
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                InlineText(
                                    line,
                                    MaterialTheme.typography.bodyLarge,
                                    linkColor,
                                    context,
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                is Block.BulletList -> Column(Modifier.padding(vertical = 2.dp)) {
                    block.items.forEachIndexed { index, item ->
                        DirectedRow(blockDirection(inlineDirectionSource(item))) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    if (block.ordered) "${index + 1}." else "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                InlineText(
                                    item,
                                    MaterialTheme.typography.bodyLarge,
                                    linkColor,
                                    context,
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                is Block.Code -> DirectedRow(blockDirection(block.text)) {
                    val textDirection = LocalTextDirection.current
                    SelectionContainer {
                        Text(
                            block.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = textDirection,
                                textAlign = TextAlign.Start,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        )
                    }
                }
                is Block.Table -> MarkdownTable(
                    table = block,
                    onOpen = { onTable(block) },
                )
                is Block.Image -> ChatImage(
                    url = block.url,
                    alt = block.alt,
                    onClick = { onImage(block.alt, block.url) },
                )
                Block.Rule -> Box(
                    Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .padding(vertical = 0.5.dp),
                )
            }
        }
    }
}

@Composable
private fun DirectedLines(
    inlines: List<Inline>,
    style: androidx.compose.ui.text.TextStyle,
    linkColor: Color,
    context: Context,
) {
    val lines = remember(inlines) { splitInlineLines(inlines) }
    Column(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            DirectedRow(blockDirection(inlineDirectionSource(line))) {
                InlineText(line, style, linkColor, context, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DirectedRow(direction: BlockDirection, content: @Composable () -> Unit) {
    val layout = if (direction == BlockDirection.Ltr) LayoutDirection.Ltr else LayoutDirection.Rtl
    val textDirection = if (direction == BlockDirection.Ltr) TextDirection.Ltr else TextDirection.Rtl
    CompositionLocalProvider(
        LocalLayoutDirection provides layout,
        LocalTextDirection provides textDirection,
    ) {
        content()
    }
}

private val LocalTextDirection = androidx.compose.runtime.staticCompositionLocalOf { TextDirection.Content }

@Composable
private fun InlineText(
    inlines: List<Inline>,
    style: androidx.compose.ui.text.TextStyle,
    linkColor: Color,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val textDirection = LocalTextDirection.current
    val annotated = remember(inlines, linkColor) {
        buildAnnotatedString {
            appendInlines(inlines, linkColor) { url -> openChatLink(context, url) }
        }
    }
    Text(
        annotated,
        style = style.copy(textDirection = textDirection, textAlign = TextAlign.Start),
        modifier = modifier,
    )
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<Inline>,
    linkColor: Color,
    open: (String) -> Unit,
) {
    inlines.forEach { inline ->
        when (inline) {
            is Inline.Text -> append(inline.value)
            Inline.LineBreak -> append("\n")
            is Inline.Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFFE8EEF5),
                    ),
                )
                append(ltrIsolate(inline.value))
                pop()
            }
            is Inline.Style -> {
                pushStyle(
                    SpanStyle(
                        fontWeight = if (inline.bold) FontWeight.Bold else null,
                        fontStyle = if (inline.italic) FontStyle.Italic else null,
                    ),
                )
                appendInlines(inline.children, linkColor, open)
                pop()
            }
            is Inline.Link -> {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = inline.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = { open(inline.url) },
                    ),
                ) {
                    append("\u2066")
                    appendInlines(inline.children, linkColor, open)
                    append("\u2069")
                }
            }
        }
    }
}

fun copyPlainText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
}

@Composable
fun ChatImage(
    url: String,
    alt: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }
    if (url.isBlank() || failed) {
        if (alt.isNotBlank()) {
            Text(alt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    AsyncImage(
        model = url,
        contentDescription = alt.ifBlank { "תמונה" },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 360.dp)
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        contentScale = ContentScale.FillWidth,
        onError = { failed = true },
    )
}
