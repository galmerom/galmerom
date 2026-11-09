package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileIdentityTest {
    @Test
    fun relink_mergesTheStaleIdWithTheCurrentLookup_andKeepsPlace() {
        val stale = tile("id:5", contactId = 5, lookup = "", phone = "050-111-1111", short = "שחרי", photo = null, added = 1)
        val fresh = tile("0rNEW", contactId = 9, lookup = "0rNEW", phone = "+972501111111", short = "שחר", photo = "new.jpg", added = 2)
        val other = tile("0rOTHER", contactId = 3, lookup = "0rOTHER", phone = "052-000-0000", short = "דנה", photo = null, added = 3)
        val merge = mergeDuplicateTiles(
            saved = listOf(stale, other, fresh),
            resolved = mapOf(
                "id:5" to ContactIdentity("0rNEW", 9, "0rNEW", "שחר כהן"),
                "0rNEW" to ContactIdentity("0rNEW", 9, "0rNEW", "שחר כהן"),
                "0rOTHER" to ContactIdentity("0rOTHER", 3, "0rOTHER", "דנה"),
            ),
            frozenOrder = listOf("id:5", "0rOTHER", "0rNEW"),
        )
        assertEquals(listOf("0rNEW", "0rOTHER"), merge.contacts.map { it.id })
        assertEquals(listOf("0rNEW", "0rOTHER"), merge.frozenOrder)
        val kept = merge.contacts.first()
        assertEquals("שחרי", kept.shortName)
        assertEquals("new.jpg", kept.customPhotoFile)
        assertEquals(9L, kept.contactId)
        assertEquals("0rNEW", kept.lookupKey)
        assertEquals(emptyList<String>(), merge.droppedPhotoFiles)
    }

    @Test
    fun relink_dropsTheExtraCustomPhoto_andKeepsAnUnresolvedTile() {
        val first = tile("a", contactId = 1, lookup = "a", phone = "050", short = "א", photo = "a.jpg", added = 1)
        val second = tile("b", contactId = 2, lookup = "b", phone = "050", short = "ב", photo = "b.jpg", added = 2)
        val orphan = tile("id:8", contactId = 8, lookup = "", phone = "053", short = "ג", photo = null, added = 4)
        val merge = mergeDuplicateTiles(
            saved = listOf(first, second, orphan),
            resolved = mapOf(
                "a" to ContactIdentity("a", 1, "a", "א בי"),
                "b" to ContactIdentity("a", 1, "a", "א בי"),
            ),
            frozenOrder = listOf("b", "a", "id:8"),
        )
        assertEquals(listOf("a", "id:8"), merge.contacts.map { it.id })
        assertEquals(listOf("a", "id:8"), merge.frozenOrder)
        assertEquals("ב", merge.contacts.first().shortName)
        assertEquals("b.jpg", merge.contacts.first().customPhotoFile)
        assertEquals(listOf("a.jpg"), merge.droppedPhotoFiles)
    }

    @Test
    fun picker_matchesLookupOrPhone_andNotADifferentNumber() {
        val saved = listOf(tile("id:4", contactId = 4, lookup = "", phone = "050-123-4567", short = "שחר", photo = null, added = 1))
        assertEquals(
            "id:4",
            savedTileFor("0rNOW", 12, listOf("+972501234567"), saved)?.id,
        )
        assertEquals(
            "id:4",
            savedTileFor("", 4, listOf("0501234567"), saved)?.id,
        )
        assertNull(savedTileFor("0rOTHER", 99, listOf("052-000-0000"), saved))
    }

    private fun tile(
        id: String,
        contactId: Long,
        lookup: String,
        phone: String,
        short: String,
        photo: String?,
        added: Long,
    ) = SavedContact(
        id = id,
        contactId = contactId,
        lookupKey = lookup,
        displayName = short,
        shortName = short,
        phoneNumber = phone,
        addedAt = added,
        customPhotoFile = photo,
    )
}
