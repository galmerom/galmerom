package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.DetectedGarment

object GarmentResponseParser {
    fun parse(
        raw: String,
        allowedCategoryIds: Set<String>? = null,
        categoryNames: Map<String, String> = emptyMap(),
    ): List<DetectedGarment> {
        val arrayBody = arrayBody(raw) ?: return emptyList()
        return splitObjects(arrayBody).mapNotNull { parseObject(it, allowedCategoryIds, categoryNames) }
    }

    private fun arrayBody(raw: String): String? {
        val garments = raw.indexOf("\"garments\"")
        val start = if (garments >= 0) raw.indexOf('[', garments) else raw.indexOf('[')
        if (start < 0) return null
        val end = matchingBracket(raw, start) ?: return null
        if (end <= start) return null
        return raw.substring(start + 1, end)
    }

    private fun matchingBracket(text: String, open: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in open until text.length) {
            val character = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun parseObject(
        objectText: String,
        allowedCategoryIds: Set<String>?,
        categoryNames: Map<String, String>,
    ): DetectedGarment? {
        val fields = stringFields(objectText)
        val category = resolveCategory(fields["category"].orEmpty(), allowedCategoryIds, categoryNames) ?: return null
        val description = fields["description"]?.trim().orEmpty()
        if (description.isEmpty()) return null
        return DetectedGarment(category, description)
    }

    private fun resolveCategory(
        raw: String,
        allowedCategoryIds: Set<String>?,
        categoryNames: Map<String, String>,
    ): String? {
        val category = raw.trim()
        if (category.isEmpty()) return null
        if (allowedCategoryIds == null) return category
        if (category in allowedCategoryIds) return category
        val byName = categoryNames.entries.firstOrNull { it.key.equals(category, ignoreCase = true) }?.value
        return byName?.takeIf { it in allowedCategoryIds }
    }

    private fun splitObjects(body: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (index in body.indices) {
            val character = body[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    if (depth == 0) continue
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects += body.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun stringFields(objectText: String): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        var index = 0
        while (index < objectText.length) {
            if (objectText[index] != '"') {
                index++
                continue
            }
            val key = readString(objectText, index) ?: break
            index = skipWhitespace(objectText, key.second)
            if (index >= objectText.length || objectText[index] != ':') continue
            index = skipWhitespace(objectText, index + 1)
            if (index >= objectText.length || objectText[index] != '"') continue
            val value = readString(objectText, index) ?: break
            fields[key.first] = value.first
            index = value.second
        }
        return fields
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun readString(text: String, start: Int): Pair<String, Int>? {
        if (text.getOrNull(start) != '"') return null
        val out = StringBuilder()
        var index = start + 1
        while (index < text.length) {
            val character = text[index]
            if (character == '\\' && index + 1 < text.length) {
                val escaped = text[index + 1]
                out.append(if (escaped == 'n') '\n' else escaped)
                index += 2
                continue
            }
            if (character == '"') return out.toString() to (index + 1)
            out.append(character)
            index++
        }
        return null
    }
}
