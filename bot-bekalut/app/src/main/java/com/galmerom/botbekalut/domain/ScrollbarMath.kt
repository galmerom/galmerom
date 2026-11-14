package com.galmerom.botbekalut.domain

/** Where the feed is, from 0 (top) to 1 (end), from the first visible item and how far it is scrolled. */
fun feedScrollProgress(
    totalItems: Int,
    visibleItems: Int,
    firstIndex: Int,
    firstFraction: Float,
    atEnd: Boolean,
): Float {
    if (totalItems <= 0) return 0f
    if (atEnd) return 1f
    val range = maxOf(totalItems - visibleItems, 1)
    return ((firstIndex + firstFraction.coerceIn(0f, 1f)) / range).coerceIn(0f, 1f)
}

/** Thumb length in px: proportional to visible/total items, never shorter than [minPx], never longer than the track. */
fun feedThumbLength(totalItems: Int, visibleItems: Int, trackPx: Float, minPx: Float): Float {
    if (totalItems <= 0 || trackPx <= 0f) return 0f
    val proportional = trackPx * visibleItems.toFloat() / totalItems
    return proportional.coerceIn(minOf(minPx, trackPx), trackPx)
}

data class FeedScrollTarget(val index: Int, val fraction: Float)

/** The item (and how far into it, 0..1) that a thumb at [progress] stands for. */
fun feedScrollTarget(progress: Float, totalItems: Int, visibleItems: Int): FeedScrollTarget {
    if (totalItems <= 0) return FeedScrollTarget(0, 0f)
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped >= 0.995f) return FeedScrollTarget(totalItems - 1, 0f)
    val position = clamped * maxOf(totalItems - visibleItems, 1)
    val index = position.toInt().coerceIn(0, totalItems - 1)
    return FeedScrollTarget(index, (position - index).coerceIn(0f, 1f))
}
