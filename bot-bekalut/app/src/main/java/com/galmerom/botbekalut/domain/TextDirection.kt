package com.galmerom.botbekalut.domain

enum class BlockDirection {
    Rtl,
    Ltr,
}

private const val LRI = "\u2066"
private const val PDI = "\u2069"

/** Wraps inline code and URLs so they stay left-to-right inside either paragraph direction. */
fun ltrIsolate(value: String): String = LRI + value + PDI

/**
 * Direction of one chat block from its first strong character.
 * Hebrew and Arabic are RTL. Latin is LTR.
 * Leading emoji, digits, punctuation, markdown markers, and http(s) URLs are skipped.
 * When nothing strong remains, the block is LTR (a bare URL, number, or emoji).
 */
fun blockDirection(text: String): BlockDirection {
    var index = 0
    while (index < text.length) {
        if (isUrlAt(text, index)) {
            index = skipUrl(text, index)
            continue
        }
        val codePoint = text.codePointAt(index)
        val width = Character.charCount(codePoint)
        if (!isSkippable(codePoint)) {
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                -> return BlockDirection.Rtl
                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                -> return BlockDirection.Ltr
            }
        }
        index += width
    }
    return BlockDirection.Ltr
}

/** Visible text used to choose direction. Link targets are omitted; labels are kept. */
fun inlineDirectionSource(inlines: List<Inline>): String = buildString {
    fun walk(list: List<Inline>) {
        list.forEach { inline ->
            when (inline) {
                is Inline.Text -> append(inline.value)
                is Inline.Code -> append(inline.value)
                Inline.LineBreak -> append('\n')
                is Inline.Style -> walk(inline.children)
                is Inline.Link -> walk(inline.children)
            }
        }
    }
    walk(inlines)
}

/** Splits a block on hard line breaks so each line can choose its own direction. */
fun splitInlineLines(inlines: List<Inline>): List<List<Inline>> {
    val lines = mutableListOf<List<Inline>>()
    val current = mutableListOf<Inline>()
    inlines.forEach { inline ->
        if (inline is Inline.LineBreak) {
            if (current.isNotEmpty()) {
                lines += current.toList()
                current.clear()
            }
        } else {
            current += inline
        }
    }
    if (current.isNotEmpty()) lines += current.toList()
    return lines
}

private fun isUrlAt(text: String, index: Int): Boolean =
    text.startsWith("https://", index) || text.startsWith("http://", index)

private fun skipUrl(text: String, start: Int): Int {
    var index = start
    while (index < text.length) {
        val char = text[index]
        if (char.isWhitespace() || char == ')' || char == '>' || char == '"') break
        index++
    }
    return index
}

private fun isSkippable(codePoint: Int): Boolean {
    if (Character.isWhitespace(codePoint) || Character.isDigit(codePoint)) return true
    return when (Character.getType(codePoint)) {
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt(),
        Character.SPACE_SEPARATOR.toInt(),
        Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(),
        Character.FORMAT.toInt(),
        Character.SURROGATE.toInt(),
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        -> true
        else -> false
    }
}
