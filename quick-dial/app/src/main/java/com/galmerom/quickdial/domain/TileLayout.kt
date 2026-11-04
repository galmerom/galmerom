package com.galmerom.quickdial.domain

const val TILE_RATIO_MIN = 0.8f
const val TILE_RATIO_MAX = 1.6f
const val TILE_RATIO_DEFAULT = 1.15f

/**
 * Width of a typical 4-column tile. At this width the short name uses the
 * chosen small, medium, or large size; wider tiles (fewer columns) scale it up.
 */
const val REFERENCE_TILE_WIDTH_DP = 88f

fun tileRatio(value: Float): Float = when {
    value.isNaN() -> TILE_RATIO_DEFAULT
    else -> value.coerceIn(TILE_RATIO_MIN, TILE_RATIO_MAX)
}

/** Two lines of the short name, plus a little padding, in dp at the default font scale. */
fun captionHeightDp(fontSp: Float): Float {
    if (fontSp <= 0f) return 0f
    return fontSp * 1.25f * 2f + 8f
}

/**
 * The photo is always a square whose side is [tileWidthDp].
 * [ratio] is the wanted tile height divided by that width.
 * When that wanted height cannot hold the square plus [captionHeightDp],
 * the tile grows so the photo stays square and the caption stays fully visible.
 */
fun tileHeightDp(tileWidthDp: Float, ratio: Float, captionHeightDp: Float): Float {
    if (tileWidthDp <= 0f) return captionHeightDp.coerceAtLeast(0f)
    val desired = tileWidthDp * tileRatio(ratio)
    val minimum = tileWidthDp + captionHeightDp.coerceAtLeast(0f)
    return maxOf(desired, minimum)
}

fun shortNameFontSp(
    baseSp: Int,
    tileWidthDp: Float,
    referenceWidthDp: Float = REFERENCE_TILE_WIDTH_DP,
): Float {
    if (baseSp <= 0 || tileWidthDp <= 0f || referenceWidthDp <= 0f) return baseSp.toFloat().coerceAtLeast(0f)
    return baseSp * (tileWidthDp / referenceWidthDp)
}

/** Letter inside the colored circle, growing with the square photo. */
fun letterFontSp(tileWidthDp: Float): Float = (tileWidthDp * 0.38f).coerceAtLeast(14f)
