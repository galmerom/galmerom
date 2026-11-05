package com.galmerom.quickdial.domain

import java.util.Locale

private val hebrew = Locale.forLanguageTag("he")

/** Case-folded letter order. Hebrew code points already follow alef-bet order. */
fun nameOrder(left: String, right: String): Int =
    left.lowercase(hebrew).compareTo(right.lowercase(hebrew))

fun normalizeName(raw: String): String {
    val stripped = StringBuilder()
    for (ch in raw) {
        if (ch in '\u0591'..'\u05C7') continue
        if (ch == '\u05F3' || ch == '\u05F4' || ch == '\'' || ch == '"' || ch == '`') continue
        val mapped = when (ch) {
            'ך' -> 'כ'
            'ם' -> 'מ'
            'ן' -> 'נ'
            'ף' -> 'פ'
            'ץ' -> 'צ'
            else -> ch
        }
        stripped.append(mapped)
    }
    return stripped.toString()
        .lowercase(hebrew)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex(" +"), " ")
}

fun defaultShortName(fullName: String): String {
    val cleaned = fullName.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isEmpty()) return ""
    return cleaned.substringBefore(' ').take(24)
}

fun cleanShortName(raw: String, fullName: String): String {
    val cleaned = raw.trim().replace(Regex("\\s+"), " ").take(24)
    if (cleaned.isNotEmpty()) return cleaned
    val fallback = defaultShortName(fullName)
    return fallback.ifBlank { fullName.trim().take(24) }.ifBlank { "?" }
}

fun matchesContactQuery(name: String, numbers: List<String>, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    val normalizedQuery = normalizeName(trimmed)
    if (normalizedQuery.isNotEmpty() && normalizeName(name).contains(normalizedQuery)) return true
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length >= 2 && numbers.any { number -> number.filter { it.isDigit() }.contains(digits) }) {
        return true
    }
    return false
}

fun initialLetter(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return trimmed.first().toString()
}

fun avatarIndex(name: String, paletteSize: Int): Int {
    if (paletteSize <= 0) return 0
    val hash = name.fold(0) { acc, ch -> (acc * 31 + ch.code) and 0x7fffffff }
    return hash % paletteSize
}
