package com.galmerom.quickdial.domain

data class ContactIdentity(
    val id: String,
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
)

fun stableContactId(lookupKey: String, contactId: Long): String =
    lookupKey.trim().ifBlank { "id:$contactId" }

data class TileMerge(
    val contacts: List<SavedContact>,
    val frozenOrder: List<String>,
    val droppedPhotoFiles: List<String>,
)

/**
 * Point each saved tile at the current contact, then collapse tiles that are
 * the same person. [resolved] maps a saved tile id to the contact it is now.
 * Tiles missing from the map stay as they are. The earlier tile keeps its
 * place; a custom short name and a custom photo survive from either copy.
 */
fun mergeDuplicateTiles(
    saved: List<SavedContact>,
    resolved: Map<String, ContactIdentity>,
    frozenOrder: List<String>,
): TileMerge {
    if (saved.isEmpty()) return TileMerge(emptyList(), emptyList(), emptyList())
    val rank = frozenOrder.withIndex().associate { it.value to it.index }
    val groups = linkedMapOf<String, MutableList<IndexedValue<SavedContact>>>()
    saved.forEachIndexed { index, contact ->
        val key = resolved[contact.id]?.id ?: contact.id
        groups.getOrPut(key) { mutableListOf() }.add(IndexedValue(index, contact))
    }
    val merged = mutableListOf<SavedContact>()
    val oldToNew = mutableMapOf<String, String>()
    val droppedPhotos = mutableListOf<String>()
    for ((_, members) in groups) {
        val ordered = members.sortedWith(compareBy({ rank[it.value.id] ?: Int.MAX_VALUE }, { it.index }))
        val primary = ordered.first().value
        val identity = resolved[primary.id]
        val displayName = identity?.displayName?.takeIf { it.isNotBlank() } ?: primary.displayName
        val photo = ordered.firstNotNullOfOrNull { it.value.customPhotoFile?.takeIf(String::isNotBlank) }
        for (extra in ordered) {
            val file = extra.value.customPhotoFile
            if (!file.isNullOrBlank() && file != photo) droppedPhotos += file
        }
        val kept = primary.copy(
            id = identity?.id ?: primary.id,
            contactId = identity?.contactId ?: primary.contactId,
            lookupKey = identity?.lookupKey ?: primary.lookupKey,
            displayName = displayName,
            shortName = chooseShortName(ordered.map { it.value }, displayName),
            addedAt = ordered.minOf { it.value.addedAt },
            customPhotoFile = photo,
        )
        merged += kept
        ordered.forEach { oldToNew[it.value.id] = kept.id }
    }
    val keptIds = merged.map { it.id }.toSet()
    val order = frozenOrder.mapNotNull { id ->
        val mapped = oldToNew[id] ?: id
        mapped.takeIf { it in keptIds }
    }.distinct()
    return TileMerge(merged, order, droppedPhotos.distinct())
}

fun chooseShortName(members: List<SavedContact>, displayName: String): String {
    val fallback = defaultShortName(displayName)
    return members.map { it.shortName.trim() }.firstOrNull { it.isNotEmpty() && it != fallback }
        ?: members.firstOrNull()?.shortName?.trim().orEmpty().ifBlank { fallback }
}

/** The saved tile for this phone contact, if it is already on the grid. */
fun savedTileFor(
    lookupKey: String,
    contactId: Long,
    phoneNumbers: List<String>,
    saved: List<SavedContact>,
): SavedContact? {
    val id = stableContactId(lookupKey, contactId)
    saved.find { tile ->
        tile.id == id ||
            (lookupKey.isNotBlank() && (tile.lookupKey == lookupKey || tile.id == lookupKey)) ||
            (contactId > 0 && tile.contactId == contactId)
    }?.let { return it }
    val keys = phoneNumbers.map(::phoneKey).filter { it.length >= 7 }.toSet()
    if (keys.isEmpty()) return null
    return saved.find { phoneKey(it.phoneNumber) in keys }
}
