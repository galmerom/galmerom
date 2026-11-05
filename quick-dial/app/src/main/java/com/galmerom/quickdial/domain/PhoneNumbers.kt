package com.galmerom.quickdial.domain

/**
 * National significant number for matching. Israeli numbers that arrive as
 * 050…, +972…, or 00972… collapse to the same key. Short codes stay short and
 * then fail [samePhone] so they are not glued together.
 */
fun phoneKey(raw: String): String {
    var digits = raw.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    if (digits.startsWith("972")) digits = digits.removePrefix("972")
    return digits.trimStart('0')
}

fun samePhone(a: String, b: String): Boolean {
    val left = phoneKey(a)
    val right = phoneKey(b)
    return left.length >= 7 && left == right
}

/** Digits safe to put after `tel:`. Keeps a leading plus. */
fun dialableNumber(raw: String): String {
    val trimmed = raw.trim()
    return buildString {
        trimmed.forEach { ch ->
            if (ch.isDigit() || ch == '*' || ch == '#') append(ch)
            if (ch == '+' && isEmpty()) append('+')
        }
    }
}

/**
 * wa.me wants a country code and no plus. National Israeli numbers get 972.
 * A WhatsApp jid (`9725…@s.whatsapp.net`) is accepted too.
 */
fun whatsAppDigits(raw: String): String? {
    val local = raw.substringBefore("@")
    var digits = local.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    if (digits.isEmpty()) return null
    val international = when {
        digits.startsWith("972") -> digits
        else -> "972" + digits.trimStart('0')
    }
    return international.takeIf { it.length in 8..15 && it != "972" }
}

data class NumberOption(
    val number: String,
    val superPrimary: Boolean = false,
    val primary: Boolean = false,
    val mobile: Boolean = false,
)

fun numberRank(option: NumberOption): Int =
    (if (option.superPrimary) 4 else 0) +
        (if (option.primary) 2 else 0) +
        (if (option.mobile) 1 else 0)

/** One entry per distinct number. A later, more "primary" copy wins the slot. */
fun distinctNumbers(options: List<NumberOption>): List<NumberOption> {
    val winner = linkedMapOf<String, NumberOption>()
    for (option in options) {
        val key = phoneKey(option.number).ifBlank { option.number }
        val existing = winner[key]
        if (existing == null || numberRank(option) > numberRank(existing)) {
            winner[key] = option
        }
    }
    return winner.values.toList()
}

/** Index of the number to preselect. Primary beats mobile beats first. */
fun suggestedIndex(options: List<NumberOption>): Int {
    if (options.isEmpty()) return -1
    var bestIndex = 0
    var bestRank = numberRank(options[0])
    for (index in 1 until options.size) {
        val rank = numberRank(options[index])
        if (rank > bestRank) {
            bestIndex = index
            bestRank = rank
        }
    }
    return bestIndex
}
