package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.DetectedGarment
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.ErrorKind
import com.galmerom.outfittracker.domain.model.Meeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainLogicTest {
    @Test
    fun targetSize_scalesLongestEdgeTo1280() {
        assertEquals(1280 to 960, ImageSizing.targetSize(4000, 3000))
        assertEquals(960 to 1280, ImageSizing.targetSize(3000, 4000))
        assertEquals(1280 to 1280, ImageSizing.targetSize(2000, 2000))
        assertEquals(800 to 600, ImageSizing.targetSize(800, 600))
        assertEquals(0 to 0, ImageSizing.targetSize(0, 400))
    }

    @Test
    fun orientedBounds_swapsForQuarterTurns() {
        assertEquals(3000 to 4000, ImageSizing.orientedBounds(4000, 3000, 90))
        assertEquals(4000 to 3000, ImageSizing.orientedBounds(4000, 3000, 180))
    }

    @Test
    fun inSampleSize_powersOfTwoUntilTargetFits() {
        assertEquals(2, ImageSizing.inSampleSize(4000, 3000, 1280, 960))
        assertEquals(1, ImageSizing.inSampleSize(1000, 800, 1280, 960))
    }

    @Test
    fun entitySuggestions_rankPrefixBeforeContains_andOfferCreate() {
        val entities = listOf(
            Entity("1", "דנה כהן", EntityType.PERSON),
            Entity("2", "פורום דנה", EntityType.FORUM),
            Entity("3", "מיכל", EntityType.PERSON),
        )
        assertEquals(listOf("1", "2"), EntitySuggestions.match("דנה", entities).map { it.id })
        assertTrue(EntitySuggestions.match("  ", entities).isEmpty())
        assertTrue(EntitySuggestions.hasExactMatch("דנה כהן", entities))
        assertFalse(EntitySuggestions.hasExactMatch("דנה", entities))
        assertTrue(EntitySuggestions.shouldOfferCreate("דנה", entities))
        assertFalse(EntitySuggestions.shouldOfferCreate("דנה כהן", entities))
        assertFalse(EntitySuggestions.shouldOfferCreate("   ", entities))
    }

    @Test
    fun meetingWindow_showsThreeThenMore_newestFirst() {
        val meetings = listOf(
            meeting("a", LocalDate.of(2026, 1, 1), "דנה"),
            meeting("b", LocalDate.of(2026, 3, 1), "מיכל"),
            meeting("c", LocalDate.of(2026, 2, 1), "דנה"),
            meeting("d", LocalDate.of(2026, 4, 1), "פורום"),
        )
        val filtered = MeetingWindow.filter(meetings, "דנה")
        assertEquals(listOf("c", "a"), filtered.map { it.id })
        assertEquals(listOf("c", "a"), MeetingWindow.visible(filtered, pages = 1).map { it.id })
        assertFalse(MeetingWindow.hasMore(filtered, pages = 1))

        val all = MeetingWindow.filter(meetings, "")
        assertEquals(listOf("d", "b", "c"), MeetingWindow.visible(all, pages = 1).map { it.id })
        assertTrue(MeetingWindow.hasMore(all, pages = 1))
        assertEquals(4, MeetingWindow.visible(all, pages = 2).size)
        assertFalse(MeetingWindow.hasMore(all, pages = 2))
    }

    @Test
    fun wardrobeRules_hideFromSelection_andMatchDetection() {
        val visible = item("1", "חולצה לבנה", ClothingCategory.SHIRT, hidden = false)
        val hidden = item("2", "חולצה שחורה", ClothingCategory.SHIRT, hidden = true)
        val pants = item("3", "מכנסיים כהים", ClothingCategory.PANTS, hidden = false)
        val items = listOf(visible, hidden, pants)

        assertEquals(listOf("1", "3"), WardrobeRules.selectable(items).map { it.id })
        assertEquals(
            "1",
            WardrobeRules.match(DetectedGarment(ClothingCategory.SHIRT, "חולצה לבנה"), items)?.id,
        )
        assertEquals(
            "3",
            WardrobeRules.match(DetectedGarment(ClothingCategory.PANTS, "מכנסיים"), items)?.id,
        )
        assertNull(WardrobeRules.match(DetectedGarment(ClothingCategory.SHIRT, "חולצה שחורה"), items))
    }

    @Test
    fun parser_readsFencedJson_andSkipsUnknownCategories() {
        val raw = """
            הנה התוצאה:
            ```json
            [
              {"description":"חולצה לבנה","category":"shirt"},
              {"category":"hat","description":"כובע"},
              {"category":"shoes","description":"  "}
            ]
            ```
        """.trimIndent()
        val parsed = GarmentResponseParser.parse(raw)
        assertEquals(listOf(DetectedGarment(ClothingCategory.SHIRT, "חולצה לבנה")), parsed)
        assertTrue(GarmentResponseParser.parse("no json").isEmpty())
    }

    @Test
    fun dates_roundTripUtcAndIso() {
        val date = LocalDate.of(2026, 11, 4)
        assertEquals(date, Dates.fromEpochMillis(Dates.toEpochMillis(date)))
        assertEquals("2026-11-04", Dates.formatIso(date))
        assertEquals(date, Dates.parseIso("2026-11-04"))
        assertNull(Dates.parseIso("nope"))
    }

    @Test
    fun errorKind_detectsPermissionFailures() {
        assertEquals(ErrorKind.PERMISSION, IllegalStateException("PERMISSION_DENIED").errorKind())
        assertEquals(
            ErrorKind.PERMISSION,
            RuntimeException("User does not have permission to access this object").errorKind(),
        )
        assertEquals(ErrorKind.OTHER, IllegalStateException("offline").errorKind())
    }

    private fun meeting(id: String, date: LocalDate, entityName: String) = Meeting(
        id = id,
        date = date,
        entityId = id,
        entityName = entityName,
        clothingItemIds = emptyList(),
        photoUrl = null,
        note = "",
    )

    private fun item(id: String, description: String, category: ClothingCategory, hidden: Boolean) =
        ClothingItem(id, description, category, photoUrl = null, hidden = hidden)
}
