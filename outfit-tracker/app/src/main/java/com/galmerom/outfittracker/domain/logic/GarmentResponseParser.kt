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

    fun parseIds(raw: String, allowedIds: Set<String>): List<String> {
        val body = arrayBody(raw) ?: return emptyList()
        val ids = mutableListOf<String>()
        var index = 0
        while (index < body.length) {
            if (body[index] != '"') {
                index++
                continue
            }
            val value = readString(body, index) ?: break
            if (value.first in allowedIds && value.first !in ids) ids += value.first
            index = value.second
        }
        return ids
    }

    fun parseUnmatched(raw: String, allowedCategoryIds: Set<String>): List<String?> {
        val body = arrayBody(raw, "unmatched") ?: return emptyList()
        val objects = splitObjects(body)
        if (objects.isNotEmpty()) {
            return objects.map { objectText ->
                stringFields(objectText)["category"]?.trim()?.takeIf { it in allowedCategoryIds }
            }
        }
        val ids = mutableListOf<String?>()
        var index = 0
        while (index < body.length) {
            if (body[index] != '"') {
                index++
                continue
            }
            val value = readString(body, index) ?: break
            ids += value.first.takeIf { it in allowedCategoryIds }
            index = value.second
        }
        return ids
    }

    private fun arrayBody(raw: String, key: String? = null): String? {
        val keyed = if (key != null) {
            val at = raw.indexOf("\"$key\"")
            if (at < 0) null else raw.indexOf('[', at).takeIf { it >= 0 }
        } else {
            listOf("\"itemIds\"", "\"garments\"").firstNotNullOfOrNull { name ->
                val at = raw.indexOf(name)
                if (at < 0) null else raw.indexOf('[', at).takeIf { it >= 0 }
            }
        }
        val start = keyed ?: if (key == null) raw.indexOf('[') else -1
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
