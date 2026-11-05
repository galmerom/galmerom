package com.galmerom.quickdial.domain

data class NamedContact(
    val id: String,
    val fullName: String,
    val shortName: String,
)

sealed interface NameMatch {
    data class One(val id: String) : NameMatch

    data class Many(val ids: List<String>) : NameMatch

    data object None : NameMatch
}

private val callPrefixes = listOf(
    "התקשר אל",
    "התקשר ל",
    "תתקשר אל",
    "תתקשר ל",
    "תתקשרי אל",
    "תתקשרי ל",
    "חייג אל",
    "חייג ל",
    "לחייג אל",
    "לחייג ל",
    "call",
).sortedByDescending { it.length }

private val leadingCallWords = setOf(
    "התקשר",
    "תתקשר",
    "תתקשרי",
    "חייג",
    "לחייג",
    "call",
)

private val fillers = setOf(
    "בבקשה",
    "נא",
    "רגע",
    "עכשיו",
    "תודה",
    "את",
    "אל",
    "ל",
    "של",
    "עם",
    "אני",
    "רוצה",
)

private const val MAX_PREFIX = 8
private const val MAX_FUZZY = 4

/**
 * Match a Hebrew (or mixed) phrase to grid contacts.
 * Exact full name, short name, or name-word hits win together.
 * A unique prefix or a single close speech error is confident.
 * Several plausible contacts are returned for the user to pick.
 * Anything vague is [NameMatch.None] and must not place a call.
 */
fun matchSpokenName(spoken: String, contacts: List<NamedContact>): NameMatch {
    val phrase = stripCallPhrase(normalizeName(spoken))
    if (phrase.isBlank() || contacts.isEmpty()) return NameMatch.None
    val prepared = contacts.map { Prepared(it) }
    val variants = phraseVariants(phrase)

    for (variant in variants) {
        val exact = prepared.filter { it.matchesExact(variant) }
        if (exact.isNotEmpty()) return toMatch(exact)
    }
    for (variant in variants) {
        if (variant.contains(' ') || variant.length < 3) continue
        val prefix = prepared.filter { it.matchesPrefix(variant) }
        if (prefix.isNotEmpty()) {
            return if (prefix.size > MAX_PREFIX) NameMatch.None else toMatch(prefix)
        }
    }
    for (variant in variants) {
        val matched = fuzzy(variant, prepared)
        if (matched != NameMatch.None) return matched
    }
    return NameMatch.None
}

/** First decisive hypothesis wins. Later guesses are only a fallback for no match. */
fun matchHypotheses(phrases: List<String>, contacts: List<NamedContact>): NameMatch {
    for (phrase in phrases) {
        if (phrase.isBlank()) continue
        val match = matchSpokenName(phrase, contacts)
        if (match != NameMatch.None) return match
    }
    return NameMatch.None
}

internal fun stripCallPhrase(normalized: String): String {
    var phrase = normalized.trim()
    for (prefix in callPrefixes) {
        if (phrase == prefix) return ""
        if (phrase.startsWith("$prefix ")) {
            phrase = phrase.removePrefix(prefix).trim()
            break
        }
    }
    val words = phrase.split(' ').filter { it.isNotBlank() }.toMutableList()
    if (words.firstOrNull() in leadingCallWords) words.removeAt(0)
    val kept = words.filter { it !in fillers }
    return if (kept.isEmpty()) words.joinToString(" ") else kept.joinToString(" ")
}

/** Also try the phrase with a leading attached ל (לדנה → דנה). The original is first. */
internal fun phraseVariants(phrase: String): List<String> {
    if (phrase.isBlank()) return emptyList()
    val words = phrase.split(' ')
    val first = words.first()
    if (first.length >= 3 && first.startsWith("ל")) {
        val stripped = (listOf(first.drop(1)) + words.drop(1)).joinToString(" ")
        if (stripped.isNotBlank() && stripped != phrase) return listOf(phrase, stripped)
    }
    return listOf(phrase)
}

private fun fuzzy(phrase: String, prepared: List<Prepared>): NameMatch {
    val limit = fuzzyLimit(phrase.length)
    if (limit <= 0) return NameMatch.None
    val hits = prepared.mapNotNull { contact ->
        val distance = contact.bestDistance(phrase, limit) ?: return@mapNotNull null
        contact to distance
    }
    if (hits.isEmpty() || hits.size > MAX_FUZZY) return NameMatch.None
    return toMatch(hits.map { it.first })
}

private fun fuzzyLimit(length: Int): Int = when {
    length <= 3 -> 0
    length <= 6 -> 1
    else -> 2
}

private fun toMatch(matches: List<Prepared>): NameMatch {
    val ids = matches
        .sortedWith { left, right ->
            val byName = nameOrder(left.displayName, right.displayName)
            if (byName != 0) byName else left.id.compareTo(right.id)
        }
        .map { it.id }
        .distinct()
    return when (ids.size) {
        0 -> NameMatch.None
        1 -> NameMatch.One(ids.first())
        else -> NameMatch.Many(ids)
    }
}

private class Prepared(contact: NamedContact) {
    val id: String = contact.id
    val displayName: String = contact.fullName
    val full: String = normalizeName(contact.fullName)
    val short: String = normalizeName(contact.shortName)
    val tokens: Set<String> = buildSet {
        addWords(full)
        addWords(short)
    }

    fun matchesExact(phrase: String): Boolean {
        if (phrase.isNotEmpty() && (phrase == full || (short.isNotEmpty() && phrase == short))) return true
        val words = phrase.split(' ')
        if (words.isEmpty() || words.any { it.length < 2 }) return false
        return words.all { it in tokens }
    }

    fun matchesPrefix(phrase: String): Boolean {
        if (phrase.length < 3) return false
        val targets = buildList {
            add(full)
            if (short.isNotEmpty()) add(short)
            addAll(tokens)
        }
        return targets.any { it.length > phrase.length && it.startsWith(phrase) }
    }

    fun bestDistance(phrase: String, limit: Int): Int? {
        val targets = buildList {
            if (full.isNotEmpty()) add(full)
            if (short.isNotEmpty()) add(short)
            addAll(tokens)
        }
        var best: Int? = null
        for (target in targets) {
            if (kotlin.math.abs(target.length - phrase.length) > limit) continue
            val distance = levenshtein(phrase, target, limit)
            if (distance <= limit && (best == null || distance < best)) best = distance
        }
        return best
    }

    private fun MutableSet<String>.addWords(value: String) {
        if (value.isBlank()) return
        for (word in value.split(' ')) {
            if (word.length >= 2) add(word)
        }
    }
}

internal fun levenshtein(left: String, right: String, limit: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1
    if (left == right) return 0
    val previous = IntArray(right.length + 1) { it }
    val current = IntArray(right.length + 1)
    for (i in 1..left.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..right.length) {
            val cost = if (left[i - 1] == right[j - 1]) 0 else 1
            current[j] = minOf(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost,
            )
            if (current[j] < rowMin) rowMin = current[j]
        }
        if (rowMin > limit) return limit + 1
        for (j in 0..right.length) previous[j] = current[j]
    }
    return previous[right.length]
}
