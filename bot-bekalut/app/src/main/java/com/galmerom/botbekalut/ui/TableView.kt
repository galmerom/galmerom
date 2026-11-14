package com.galmerom.botbekalut.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.galmerom.botbekalut.domain.Block
import com.galmerom.botbekalut.domain.BlockDirection
import com.galmerom.botbekalut.domain.Inline
import com.galmerom.botbekalut.domain.blockDirection
import com.galmerom.botbekalut.domain.inlineDirectionSource
import com.galmerom.botbekalut.domain.ltrIsolate
import com.galmerom.botbekalut.domain.parseInlines
import com.galmerom.botbekalut.domain.tableColumnWidth
import com.galmerom.botbekalut.domain.tableIsRtl

@Composable
fun MarkdownTable(
    table: Block.Table,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    maxFirstColumn: Dp = 260.dp,
    maxOtherColumn: Dp = 320.dp,
) {
    val scroll = rememberScrollState()
    val style = MaterialTheme.typography.bodyMedium.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.9f,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 0.9f,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // One width per column, shared by every row, so the columns line up and cells never wrap.
    val widths = remember(table, style, density, maxFirstColumn, maxOtherColumn) {
        val columns = table.rows.maxOfOrNull { it.size } ?: 0
        List(columns) { column ->
            var widest = 0
            table.rows.forEachIndexed { rowIndex, row ->
                val plain = inlineDirectionSource(parseInlines(row.getOrNull(column).orEmpty())).replace('\n', ' ')
                val measured = measurer.measure(
                    text = plain,
                    style = style.copy(fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal),
                    softWrap = false,
                    maxLines = 1,
                ).size.width
                if (measured > widest) widest = measured
            }
            val contentDp = with(density) { widest.toDp() }.value
            val maxDp = if (column == 0) maxFirstColumn.value else maxOtherColumn.value
            tableColumnWidth(contentDp, maxDp).dp
        }
    }
    val layout = if (remember(table) { tableIsRtl(table.rows) }) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layout) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .clickable(onClick = onOpen),
        ) {
            table.rows.forEachIndexed { index, row ->
                val background = when {
                    index == 0 -> MaterialTheme.colorScheme.primaryContainer
                    index % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surface
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                        .background(background),
                ) {
                    TableCell(
                        text = row.firstOrNull().orEmpty(),
                        header = index == 0,
                        width = widths.getOrElse(0) { 96.dp },
                        modifier = Modifier.fillMaxHeight(),
                        style = style,
                    )
                    Row(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(scroll)
                            .fillMaxHeight(),
                    ) {
                        row.drop(1).forEachIndexed { offset, cell ->
                            TableCell(
                                text = cell,
                                header = index == 0,
                                width = widths.getOrElse(offset + 1) { 96.dp },
                                modifier = Modifier.fillMaxHeight(),
                                style = style,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    header: Boolean,
    width: Dp,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val inlines = remember(text) { parseInlines(text) }
    val annotated = remember(inlines, linkColor) {
        buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal))
            appendInlinesForTable(inlines, linkColor, context)
            pop()
        }
    }
    val direction = blockDirection(text)
    val layout = if (direction == BlockDirection.Ltr) LayoutDirection.Ltr else LayoutDirection.Rtl
    val textDirection = if (direction == BlockDirection.Ltr) TextDirection.Ltr else TextDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides layout) {
        Box(
            modifier
                .width(width)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                annotated,
                style = style.copy(textDirection = textDirection, textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

private fun AnnotatedString.Builder.appendInlinesForTable(
    inlines: List<Inline>,
    linkColor: Color,
    context: android.content.Context,
) {
    inlines.forEach { inline ->
        when (inline) {
            is Inline.Text -> append(inline.value)
            Inline.LineBreak -> append(" ")
            is Inline.Code -> append(ltrIsolate(inline.value))
            is Inline.Style -> appendInlinesForTable(inline.children, linkColor, context)
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
                        linkInteractionListener = { openChatLink(context, inline.url) },
                    ),
                ) {
                    append("\u2066")
                    appendInlinesForTable(inline.children, linkColor, context)
                    append("\u2069")
                }
            }
        }
    }
}

@Composable
fun FullscreenTable(table: Block.Table, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
        ) {
            TextButton(onClick = onDismiss) {
                Text("סגירה", style = MaterialTheme.typography.titleMedium)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownTable(table = table, onOpen = {}, maxFirstColumn = 360.dp, maxOtherColumn = 480.dp)
            }
        }
    }
}
