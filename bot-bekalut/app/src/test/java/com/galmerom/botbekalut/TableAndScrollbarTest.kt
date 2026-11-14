package com.galmerom.botbekalut

import com.galmerom.botbekalut.domain.feedScrollProgress
import com.galmerom.botbekalut.domain.feedScrollTarget
import com.galmerom.botbekalut.domain.feedThumbLength
import com.galmerom.botbekalut.domain.tableColumnWidth
import com.galmerom.botbekalut.domain.tableIsRtl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableAndScrollbarTest {
    @Test
    fun columnWidthIsClamped() {
        assertEquals(48f, tableColumnWidth(contentWidthDp = 5f, maxDp = 320f), 0.001f)
        assertEquals(100f + 18f, tableColumnWidth(contentWidthDp = 100f, maxDp = 320f), 0.001f)
        assertEquals(260f, tableColumnWidth(contentWidthDp = 900f, maxDp = 260f), 0.001f)
    }

    @Test
    fun hebrewTableIsRtl() {
        val rows = listOf(
            listOf("מניה", "מחיר", "שינוי"),
            listOf("AAPL", "190.5", "+1.2%"),
            listOf("טסלה", "240", "-0.4%"),
        )
        assertTrue(tableIsRtl(rows))
    }

    @Test
    fun englishAndNumberTablesAreLtr() {
        assertFalse(tableIsRtl(listOf(listOf("Ticker", "Price"), listOf("AAPL", "190.5"))))
        assertFalse(tableIsRtl(listOf(listOf("1", "2"), listOf("3", "4"))))
        assertFalse(tableIsRtl(emptyList()))
    }

    @Test
    fun scrollProgressRunsFromTopToEnd() {
        assertEquals(0f, feedScrollProgress(10, 3, 0, 0f, atEnd = false), 0.001f)
        assertEquals(0.5f, feedScrollProgress(11, 3, 4, 0f, atEnd = false), 0.001f)
        assertEquals(1f, feedScrollProgress(10, 3, 6, 0.4f, atEnd = true), 0.001f)
        assertEquals(0f, feedScrollProgress(0, 0, 0, 0f, atEnd = true), 0.001f)
    }

    @Test
    fun thumbLengthIsProportionalButBounded() {
        assertEquals(300f, feedThumbLength(10, 3, 1000f, 100f), 0.001f)
        assertEquals(100f, feedThumbLength(100, 2, 1000f, 100f), 0.001f)
        assertEquals(1000f, feedThumbLength(3, 3, 1000f, 100f), 0.001f)
    }

    @Test
    fun scrollTargetMapsProgressToItems() {
        assertEquals(0, feedScrollTarget(0f, 20, 4).index)
        val middle = feedScrollTarget(0.5f, 20, 4)
        assertEquals(8, middle.index)
        assertEquals(0f, middle.fraction, 0.001f)
        assertEquals(19, feedScrollTarget(1f, 20, 4).index)
        assertEquals(0, feedScrollTarget(0.5f, 0, 0).index)
    }
}
