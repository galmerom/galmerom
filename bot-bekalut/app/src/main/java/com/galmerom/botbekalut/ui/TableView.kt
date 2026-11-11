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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.galmerom.botbekalut.domain.Block
import com.galmerom.botbekalut.domain.Inline
import com.galmerom.botbekalut.domain.parseInlines

@Composable
fun MarkdownTable(
    table: Block.Table,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val style = MaterialTheme.typography.bodyMedium.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.9f,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 0.9f,
    )
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
                    sticky = true,
                    modifier = Modifier.fillMaxHeight(),
                    style = style,
                )
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(scroll)
                        .fillMaxHeight(),
                ) {
                    row.drop(1).forEach { cell ->
                        TableCell(
                            text = cell,
                            header = index == 0,
                            sticky = false,
                            style = style,
                        )
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
    sticky: Boolean,
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
    Box(
        modifier
            .then(
                if (sticky) {
                    Modifier.widthIn(min = 108.dp, max = 210.dp)
                } else {
                    Modifier.widthIn(min = 96.dp)
                },
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            annotated,
            style = style,
            maxLines = if (sticky) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = sticky,
        )
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
            is Inline.Code -> append(inline.value)
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
                    appendInlinesForTable(inline.children, linkColor, context)
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
                MarkdownTable(table = table, onOpen = {})
            }
        }
    }
}
