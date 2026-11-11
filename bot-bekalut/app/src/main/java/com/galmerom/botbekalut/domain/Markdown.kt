package com.galmerom.botbekalut.domain

sealed interface Inline {
    data class Text(val value: String) : Inline
    data class Code(val value: String) : Inline
    data class Style(val bold: Boolean, val italic: Boolean, val children: List<Inline>) : Inline
    data class Link(val children: List<Inline>, val url: String) : Inline
    data object LineBreak : Inline
}

sealed interface Block {
    data class Paragraph(val inlines: List<Inline>) : Block
    data class Heading(val level: Int, val inlines: List<Inline>) : Block
    data class BulletList(val ordered: Boolean, val items: List<List<Inline>>) : Block
    data class Quote(val inlines: List<Inline>) : Block
    data class Code(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    data class Image(val alt: String, val url: String) : Block
    data object Rule : Block
}

private val imageLine = Regex("""^!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)\s*$""")
private val headingLine = Regex("""^(#{1,6})\s+(.*)$""")
private val listLine = Regex("""^\s*(?:[-*]|\d+[.)])\s+\S.*$""")
private val hrLine = Regex("""^(\*\s*){3,}|(-\s*){3,}|(_\s*){3,}$""")
private val separatorCell = Regex(""":?-{3,}:?""")
private val urlPattern = Regex("""https?://[^\s<>]+""")
private val inlineImage = Regex("""!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")

fun parseMarkdown(source: String): List<Block> {
    val normalized = inlineImage.replace(source.replace("\r\n", "\n").replace('\r', '\n')) { match ->
        "\n\n![${match.groupValues[1]}](${match.groupValues[2]})\n\n"
    }
    val lines = normalized.split('\n')
    val blocks = mutableListOf<Block>()
    var index = 0
    while (index < lines.size) {
        if (lines[index].isBlank()) {
            index++
            continue
        }
        if (lines[index].trimStart().startsWith("```")) {
            val body = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[index])
                index++
            }
            if (index < lines.size) index++
            blocks += Block.Code(body.toString())
            continue
        }
        if (isTableStart(lines, index)) {
            val header = splitRow(lines[index])
            index += 2
            val rows = mutableListOf(header)
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                rows += splitRow(lines[index])
                index++
            }
            blocks += Block.Table(normalizeTable(rows))
            continue
        }
        val imageMatch = imageLine.matchEntire(lines[index].trim())
        if (imageMatch != null) {
            blocks += Block.Image(imageMatch.groupValues[1], imageMatch.groupValues[2])
            index++
            continue
        }
        if (isHr(lines[index])) {
            blocks += Block.Rule
            index++
            continue
        }
        val headingMatch = headingLine.matchEntire(lines[index].trim())
        if (headingMatch != null) {
            blocks += Block.Heading(headingMatch.groupValues[1].length, parseInlines(headingMatch.groupValues[2]))
            index++
            continue
        }
        if (lines[index].trimStart().startsWith(">")) {
            val parts = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                parts += lines[index].trimStart().removePrefix(">").trimStart()
                index++
            }
            blocks += Block.Quote(parseInlines(parts.joinToString("\n")))
            continue
        }
        if (isListItem(lines[index])) {
            val ordered = lines[index].trimStart().first().isDigit()
            val items = mutableListOf<List<Inline>>()
            while (index < lines.size && isListItem(lines[index])) {
                val text = lines[index].trimStart().replace(Regex("""^(?:[-*]|\d+[.)])\s+"""), "")
                items += parseInlines(text)
                index++
            }
            blocks += Block.BulletList(ordered, items)
            continue
        }
        val paragraph = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines, index)) {
            paragraph += lines[index].trimEnd()
            index++
        }
        if (paragraph.isEmpty() && index < lines.size) {
            paragraph += lines[index].trimEnd()
            index++
        }
        if (paragraph.isNotEmpty()) {
            blocks += Block.Paragraph(parseInlines(paragraph.joinToString("\n")))
        }
    }
    return blocks
}

fun messagePlainText(message: ChatMessage): String {
    val blocks = parseMarkdown(message.content)
    val body = blocksToPlain(blocks).trim()
    val extras = message.images.map { it.url }.filter { url ->
        url.isNotBlank() && url !in message.content && url !in body
    }
    return (listOf(body) + extras).filter { it.isNotBlank() }.joinToString("\n")
}

fun blocksToPlain(blocks: List<Block>): String = buildString {
    blocks.forEach { block ->
        if (isNotEmpty()) append("\n\n")
        when (block) {
            is Block.Paragraph -> append(inlinesToPlain(block.inlines))
            is Block.Heading -> append(inlinesToPlain(block.inlines))
            is Block.Quote -> append(inlinesToPlain(block.inlines))
            is Block.Code -> append(block.text)
            is Block.BulletList -> block.items.forEachIndexed { index, item ->
                if (index > 0) append('\n')
                append(if (block.ordered) "${index + 1}. " else "• ")
                append(inlinesToPlain(item))
            }
            is Block.Table -> block.rows.forEachIndexed { index, row ->
                if (index > 0) append('\n')
                append(row.joinToString("\t"))
            }
            is Block.Image -> append(block.alt.ifBlank { block.url })
            Block.Rule -> append("---")
        }
    }
}

private fun inlinesToPlain(inlines: List<Inline>): String = buildString {
    fun walk(list: List<Inline>) {
        list.forEach { inline ->
            when (inline) {
                is Inline.Text -> append(inline.value)
                is Inline.Code -> append(inline.value)
                Inline.LineBreak -> append('\n')
                is Inline.Style -> walk(inline.children)
                is Inline.Link -> {
                    val shown = inlinesToPlain(inline.children)
                    append(shown)
                    if (inline.url.isNotBlank() && inline.url != shown) {
                        append(" (")
                        append(inline.url)
                        append(')')
                    }
                }
            }
        }
    }
    walk(inlines)
}

internal fun parseInlines(input: String): List<Inline> {
    val out = mutableListOf<Inline>()
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            appendText(out, buffer.toString())
            buffer.clear()
        }
    }
    var index = 0
    while (index < input.length) {
        val char = input[index]
        if (char == '\\' && index + 1 < input.length) {
            buffer.append(input[index + 1])
            index += 2
            continue
        }
        if (char == '\n') {
            flush()
            out += Inline.LineBreak
            index++
            continue
        }
        if (char == '`') {
            val end = input.indexOf('`', index + 1)
            if (end > index) {
                flush()
                out += Inline.Code(input.substring(index + 1, end))
                index = end + 1
                continue
            }
        }
        if ((input.startsWith("**", index) || input.startsWith("__", index)) && canOpenEmphasis(input, index)) {
            val marker = input.substring(index, index + 2)
            val end = input.indexOf(marker, index + 2)
            if (end > index) {
                flush()
                out += Inline.Style(bold = true, italic = false, children = parseInlines(input.substring(index + 2, end)))
                index = end + 2
                continue
            }
        }
        if ((char == '*' || char == '_') && input.getOrNull(index + 1) != char && canOpenEmphasis(input, index)) {
            val end = findSingle(input, index + 1, char)
            if (end > index) {
                flush()
                out += Inline.Style(bold = false, italic = true, children = parseInlines(input.substring(index + 1, end)))
                index = end + 1
                continue
            }
        }
        if (char == '[') {
            val link = parseLink(input, index)
            if (link != null) {
                flush()
                out += link.first
                index = link.second
                continue
            }
        }
        buffer.append(char)
        index++
    }
    flush()
    return out
}

private fun appendText(out: MutableList<Inline>, text: String) {
    var last = 0
    urlPattern.findAll(text).forEach { match ->
        if (match.range.first > last) out += Inline.Text(text.substring(last, match.range.first))
        var url = match.value
        var trailing = ""
        while (url.isNotEmpty() && url.last() in ".,);:]") {
            trailing = url.last() + trailing
            url = url.dropLast(1)
        }
        if (url.isNotEmpty()) out += Inline.Link(listOf(Inline.Text(url)), url)
        if (trailing.isNotEmpty()) out += Inline.Text(trailing)
        last = match.range.last + 1
    }
    if (last < text.length) out += Inline.Text(text.substring(last))
}

private fun parseLink(input: String, start: Int): Pair<Inline.Link, Int>? {
    val labelEnd = input.indexOf(']', start + 1)
    if (labelEnd < 0 || input.getOrNull(labelEnd + 1) != '(') return null
    val urlEnd = input.indexOf(')', labelEnd + 2)
    if (urlEnd < 0) return null
    val url = input.substring(labelEnd + 2, urlEnd).trim().substringBefore(" ").trim()
    if (url.isEmpty()) return null
    val label = input.substring(start + 1, labelEnd)
    return Inline.Link(parseInlines(label), url) to (urlEnd + 1)
}

private fun canOpenEmphasis(input: String, index: Int): Boolean {
    if (index == 0) return true
    val previous = input[index - 1]
    return previous.isWhitespace() || previous in "([{\""
}

private fun findSingle(input: String, from: Int, marker: Char): Int {
    var index = from
    while (index < input.length) {
        if (input[index] == '\\') {
            index += 2
            continue
        }
        if (input[index] == marker && input.getOrNull(index + 1) != marker) return index
        index++
    }
    return -1
}

private fun startsBlock(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    val trimmed = line.trim()
    if (trimmed.startsWith("```")) return true
    if (headingLine.matches(trimmed)) return true
    if (trimmed.startsWith(">")) return true
    if (isListItem(line)) return true
    if (imageLine.matches(trimmed)) return true
    if (isHr(line)) return true
    if (isTableStart(lines, index)) return true
    return false
}

private fun isListItem(line: String): Boolean = listLine.matches(line)

private fun isHr(line: String): Boolean = hrLine.matches(line.trim())

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    val next = lines.getOrNull(index + 1) ?: return false
    if (!lines[index].contains('|')) return false
    return isSeparator(next)
}

private fun isSeparator(line: String): Boolean {
    if (!line.contains('-') || !line.contains('|')) return false
    val cells = splitRow(line)
    return cells.isNotEmpty() && cells.all { separatorCell.matches(it.replace(" ", "")) }
}

private fun splitRow(line: String): List<String> {
    var value = line.trim()
    if (value.startsWith("|")) value = value.drop(1)
    if (value.endsWith("|")) value = value.dropLast(1)
    return value.split('|').map { it.trim() }
}

private fun normalizeTable(rows: List<List<String>>): List<List<String>> {
    val width = rows.maxOf { it.size }
    return rows.map { row -> List(width) { index -> row.getOrElse(index) { "" } } }
}
