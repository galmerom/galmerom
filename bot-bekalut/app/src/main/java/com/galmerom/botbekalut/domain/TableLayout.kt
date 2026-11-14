package com.galmerom.botbekalut.domain

/** Horizontal padding of one table cell, both sides together (8dp each), plus a little slack against rounding. */
const val TableCellPaddingDp = 18f
const val TableMinColumnDp = 48f

/** One shared column width in dp: the widest cell text plus padding, kept between the minimum and [maxDp]. */
fun tableColumnWidth(
    contentWidthDp: Float,
    maxDp: Float,
    paddingDp: Float = TableCellPaddingDp,
    minDp: Float = TableMinColumnDp,
): Float = (contentWidthDp + paddingDp).coerceIn(minDp, maxOf(minDp, maxDp))

/**
 * True when the table is mostly Hebrew or Arabic, so it should be laid out right-to-left
 * (first column on the right, horizontal scroll starting at the right).
 * Only cells that contain a letter vote, each by its own block direction. Numbers and empty cells are ignored.
 */
fun tableIsRtl(rows: List<List<String>>): Boolean {
    var rtl = 0
    var ltr = 0
    rows.forEach { row ->
        row.forEach { cell ->
            if (cell.any { it.isLetter() }) {
                if (blockDirection(cell) == BlockDirection.Rtl) rtl++ else ltr++
            }
        }
    }
    return rtl > 0 && rtl >= ltr
}
