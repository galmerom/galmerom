package com.galmerom.quickdial.domain

import kotlinx.serialization.Serializable

enum class ShortNameSize(val key: String, val sp: Int) {
    Small("small", 16),
    Medium("medium", 20),
    Large("large", 26),
    ;

    companion object {
        fun fromKey(value: String?): ShortNameSize = entries.firstOrNull { it.key == value } ?: Medium
    }
}

enum class DialTheme(val key: String) {
    Blue("blue"),
    Ocean("ocean"),
    Sage("sage"),
    Lilac("lilac"),
    Warm("warm"),
    ;

    companion object {
        fun fromKey(value: String?): DialTheme = entries.firstOrNull { it.key == value } ?: Blue
    }
}

@Serializable
data class SavedContact(
    val id: String,
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val shortName: String,
    val phoneNumber: String,
    val addedAt: Long,
)

@Serializable
data class QuickDialSettings(
    val columns: Int = 3,
    val frozen: Boolean = false,
    val frozenOrder: List<String> = emptyList(),
    val showCallBadge: Boolean = false,
    val shortNameSize: String = ShortNameSize.Medium.key,
    val theme: String = DialTheme.Blue.key,
    val contacts: List<SavedContact> = emptyList(),
) {
    fun sanitized(): QuickDialSettings {
        val unique = contacts.filter { it.id.isNotBlank() }.distinctBy { it.id }
        val ids = unique.map { it.id }.toSet()
        return copy(
            columns = columnCount(columns),
            shortNameSize = ShortNameSize.fromKey(shortNameSize).key,
            theme = DialTheme.fromKey(theme).key,
            contacts = unique,
            frozenOrder = frozenOrder.filter { it in ids }.distinct(),
        )
    }

    fun orderRequest(counts: Map<String, Int>): OrderRequest = OrderRequest(
        entries = contacts.map { contact ->
            OrderEntry(
                id = contact.id,
                name = contact.displayName,
                callCount = counts[contact.id] ?: 0,
                addedAt = contact.addedAt,
            )
        },
        frozen = frozen,
        frozenOrder = frozenOrder,
    )
}
