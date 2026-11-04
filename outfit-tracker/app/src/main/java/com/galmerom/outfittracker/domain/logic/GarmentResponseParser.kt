package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.DetectedGarment

object GarmentResponseParser {
    fun parse(raw: String): List<DetectedGarment> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return splitObjects(raw.substring(start + 1, end)).mapNotNull(::parseObject)
    }

    private fun parseObject(objectText: String): DetectedGarment? {
        val fields = stringFields(objectText)
        val category = ClothingCategory.fromWire(fields["category"]) ?: return null
        val description = fields["description"]?.trim().orEmpty()
        if (description.isEmpty()) return null
        return DetectedGarment(category, description)
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
