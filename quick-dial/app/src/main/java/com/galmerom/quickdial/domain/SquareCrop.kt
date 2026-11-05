package com.galmerom.quickdial.domain

data class SquareCrop(val x: Int, val y: Int, val side: Int)

/** Center square of a bitmap. Wider and taller photos lose the edges equally. */
fun centerSquareCrop(width: Int, height: Int): SquareCrop? {
    if (width <= 0 || height <= 0) return null
    val side = minOf(width, height)
    return SquareCrop(
        x = (width - side) / 2,
        y = (height - side) / 2,
        side = side,
    )
}
