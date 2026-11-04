package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GridOrderTest {
    private fun entry(id: String, name: String, calls: Int, addedAt: Long = 0) =
        OrderEntry(id, name, calls, addedAt)

    @Test
    fun mostCalledIsFirst_andRightmostOfTheTopRow() {
        val request = OrderRequest(
            entries = listOf(
                entry("low", "בני", 1),
                entry("high", "אבי", 9),
                entry("mid", "גיל", 4),
                entry("zero", "דנה", 0),
            ),
            frozen = false,
            frozenOrder = emptyList(),
        )
        val order = orderedIds(request)
        assertEquals(listOf("high", "mid", "low", "zero"), order)
        val rows = rowsRightmostFirst(order, columns = 3)
        assertEquals(listOf("high", "mid", "low"), rows[0])
        assertEquals("high", rows[0][0])
        assertEquals(listOf("zero"), rows[1])
    }

    @Test
    fun tieBreaksByNameThenId_andZeroCallsStayAtTheEnd() {
        val request = OrderRequest(
            entries = listOf(
                entry("b", "דני", 2),
                entry("a", "דנה", 2),
                entry("c", "דנה", 2),
                entry("new", "זאב", 0, addedAt = 5),
            ),
            frozen = false,
            frozenOrder = listOf("new", "a"),
        )
        assertEquals(listOf("a", "c", "b", "new"), orderedIds(request))
    }

    @Test
    fun hebrewNames_sortAlefBeforeBetWhenCountsMatch() {
        val request = OrderRequest(
            entries = listOf(entry("2", "בני", 0), entry("1", "אבי", 0)),
            frozen = false,
            frozenOrder = emptyList(),
        )
        assertEquals(listOf("1", "2"), orderedIds(request))
    }

    @Test
    fun freeze_keepsTheSnapshotEvenWhenCountsChange() {
        val entries = listOf(
            entry("a", "אבי", 1),
            entry("b", "בני", 50),
        )
        val frozen = OrderRequest(entries, frozen = true, frozenOrder = listOf("a", "b"))
        assertEquals(listOf("a", "b"), orderedIds(frozen))
        assertEquals(
            listOf("b", "a"),
            orderedIds(frozen.copy(frozen = false)),
        )
    }

    @Test
    fun newContactsWhileFrozen_appendByAddedTime_notByCalls() {
        val request = OrderRequest(
            entries = listOf(
                entry("a", "אבי", 1, addedAt = 1),
                entry("b", "בני", 3, addedAt = 2),
                entry("late", "משה", 100, addedAt = 20),
                entry("early", "דנה", 100, addedAt = 10),
            ),
            frozen = true,
            frozenOrder = listOf("a", "b"),
        )
        assertEquals(listOf("a", "b", "early", "late"), orderedIds(request))
    }

    @Test
    fun removedContacts_dropOutOfTheFrozenOrder() {
        val request = OrderRequest(
            entries = listOf(entry("a", "אבי", 1), entry("c", "גיל", 1)),
            frozen = true,
            frozenOrder = listOf("a", "gone", "c"),
        )
        assertEquals(listOf("a", "c"), orderedIds(request))
    }

    @Test
    fun duplicateIds_keepTheFirstEntry() {
        val request = OrderRequest(
            entries = listOf(entry("a", "אבי", 5), entry("a", "אבי", 1), entry("b", "בני", 0)),
            frozen = false,
            frozenOrder = emptyList(),
        )
        assertEquals(listOf("a", "b"), orderedIds(request))
    }

    @Test
    fun slide_movesTowardTheVisualSide_andStopsAtTheEnds() {
        val order = listOf("right", "middle", "left")
        assertEquals(listOf("middle", "right", "left"), slide(order, "middle", Slide.Right))
        assertEquals(listOf("right", "left", "middle"), slide(order, "middle", Slide.Left))
        assertEquals(order, slide(order, "right", Slide.Right))
        assertEquals(order, slide(order, "left", Slide.Left))
        assertEquals(order, slide(order, "missing", Slide.Left))
    }

    @Test
    fun fourColumns_stillStartEachRowOnTheRight() {
        val ids = listOf("1", "2", "3", "4", "5")
        assertEquals(listOf(listOf("1", "2", "3", "4"), listOf("5")), rowsRightmostFirst(ids, 4))
        assertEquals(3, columnCount(3))
        assertEquals(4, columnCount(4))
        assertEquals(3, columnCount(9))
    }

    @Test
    fun settings_sanitizeUnknownValuesAndDropOrphans() {
        val settings = QuickDialSettings(
            columns = 9,
            shortNameSize = "huge",
            theme = "nope",
            showCallBadge = false,
            contacts = listOf(
                saved("a"),
                saved("a"),
                saved(""),
            ),
            frozenOrder = listOf("missing", "a", "a"),
        ).sanitized()
        assertEquals(3, settings.columns)
        assertEquals(ShortNameSize.Medium.key, settings.shortNameSize)
        assertEquals(DialTheme.Blue.key, settings.theme)
        assertEquals(listOf("a"), settings.contacts.map { it.id })
        assertEquals(listOf("a"), settings.frozenOrder)
        assertEquals(false, settings.showCallBadge)
    }

    private fun saved(id: String) = SavedContact(
        id = id,
        contactId = 1,
        lookupKey = id,
        displayName = "שם",
        shortName = "שם",
        phoneNumber = "050",
        addedAt = 1,
    )
}
