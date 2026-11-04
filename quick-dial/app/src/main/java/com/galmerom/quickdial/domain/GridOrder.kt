package com.galmerom.quickdial.domain

data class OrderEntry(
    val id: String,
    val name: String,
    val callCount: Int,
    val addedAt: Long,
)

data class OrderRequest(
    val entries: List<OrderEntry>,
    val frozen: Boolean,
    val frozenOrder: List<String>,
)

/**
 * Visual right in an RTL grid is index 0 of each row. [Slide.Right] moves a
 * tile toward that side (lower index). [Slide.Left] moves it the other way.
 */
enum class Slide {
    Right,
    Left,
}

private val automaticOrder = Comparator<OrderEntry> { left, right ->
    val byCount = right.callCount.compareTo(left.callCount)
    if (byCount != 0) return@Comparator byCount
    val byName = nameOrder(left.name, right.name)
    if (byName != 0) byName else left.id.compareTo(right.id)
}

private val addedOrder = Comparator<OrderEntry> { left, right ->
    val byAdded = left.addedAt.compareTo(right.addedAt)
    if (byAdded != 0) return@Comparator byAdded
    val byName = nameOrder(left.name, right.name)
    if (byName != 0) byName else left.id.compareTo(right.id)
}

/**
 * Unfrozen: most outgoing calls first, ties by name, then id.
 * Frozen: the saved order, with contacts added later appended by [OrderEntry.addedAt].
 * Index 0 is the rightmost cell of the top row when the grid is laid out RTL.
 */
fun orderedIds(request: OrderRequest): List<String> {
    val entries = request.entries.distinctBy { it.id }
    if (!request.frozen) return entries.sortedWith(automaticOrder).map { it.id }
    val byId = entries.associateBy { it.id }
    val kept = request.frozenOrder.filter { it in byId }.distinct()
    val keptSet = kept.toSet()
    val extras = entries.filter { it.id !in keptSet }.sortedWith(addedOrder).map { it.id }
    return kept + extras
}

fun slide(order: List<String>, id: String, direction: Slide): List<String> {
    val index = order.indexOf(id)
    if (index < 0) return order
    val target = when (direction) {
        Slide.Right -> index - 1
        Slide.Left -> index + 1
    }
    if (target !in order.indices) return order
    val next = order.toMutableList()
    val moved = next[index]
    next[index] = next[target]
    next[target] = moved
    return next
}

/** Each inner list starts with the contact that sits on the right of that row. */
fun rowsRightmostFirst(orderedIds: List<String>, columns: Int): List<List<String>> {
    val width = columnCount(columns)
    if (orderedIds.isEmpty()) return emptyList()
    return orderedIds.chunked(width)
}

fun columnCount(value: Int): Int = when (value) {
    2 -> 2
    4 -> 4
    else -> 3
}
