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
        val visible = item("1", "חולצה לבנה", "shirt", hidden = false)
        val hidden = item("2", "חולצה שחורה", "shirt", hidden = true)
        val pants = item("3", "מכנסיים כהים", "pants", hidden = false)
        val items = listOf(visible, hidden, pants)

        assertEquals(listOf("1", "3"), WardrobeRules.selectable(items).map { it.id })
        assertEquals(
            "1",
            WardrobeRules.match(DetectedGarment("shirt", "חולצה לבנה"), items)?.id,
        )
        assertEquals(
            "3",
            WardrobeRules.match(DetectedGarment("pants", "מכנסיים"), items)?.id,
        )
        assertNull(WardrobeRules.match(DetectedGarment("shirt", "חולצה שחורה"), items))
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
        val parsed = GarmentResponseParser.parse(raw, setOf("shirt", "pants", "shoes"))
        assertEquals(listOf(DetectedGarment("shirt", "חולצה לבנה")), parsed)
        assertTrue(GarmentResponseParser.parse("no json").isEmpty())
        val wrapped = """{"garments":[{"category":"חולצה","description":"חולצה לבנה ] עם פס"},{"category":"pants","description":"מכנסיים כהים"}]}"""
        assertEquals(
            listOf(
                DetectedGarment("shirt", "חולצה לבנה ] עם פס"),
                DetectedGarment("pants", "מכנסיים כהים"),
            ),
            GarmentResponseParser.parse(wrapped, setOf("shirt", "pants"), mapOf("חולצה" to "shirt")),
        )
    }

    @Test
    fun aiErrorText_explainsDepletedGeminiCredits() {
        val reason = AiErrorText.reason(IllegalStateException("Your prepayment credits are depleted"))
        assertTrue(reason.contains("קרדיט"))
    }

    @Test
    fun categoryOrder_putsPantsAndShirtFirstUnlessAlreadyCustom() {
        val legacy = listOf(
            ClothingCategory("shirt", "חולצה", 0),
            ClothingCategory("pants", "מכנסיים", 1),
            ClothingCategory("skirt", "חצאית", 2),
            ClothingCategory("dress", "שמלה", 3),
            ClothingCategory("jacket", "ז׳קט", 4),
            ClothingCategory("shoes", "נעליים", 5),
            ClothingCategory("accessory", "אביזר", 6),
            ClothingCategory("other", "אחר", 7),
        )
        val updates = CategoryOrder.migrationUpdates(legacy)
        assertEquals(0, updates?.get("pants"))
        assertEquals(1, updates?.get("shirt"))
        assertEquals(2, updates?.get("skirt"))
        val custom = legacy.map { category ->
            if (category.id == "shoes") category.copy(sortOrder = 0) else category.copy(sortOrder = category.sortOrder + 1)
        }
        assertNull(CategoryOrder.migrationUpdates(custom))
        assertEquals(mapOf("pants" to 0, "shirt" to 1), CategoryOrder.sortOrders(listOf("pants", "shirt")))
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
        entityIds = listOf(id),
        entityNames = listOf(entityName),
        clothingItemIds = emptyList(),
        photoUrl = null,
        note = "",
    )

    private fun item(id: String, description: String, categoryId: String, hidden: Boolean) =
        ClothingItem(id, description, categoryId, photoUrl = null, hidden = hidden)

    @Test
    fun entityPicker_ordersByMeetingFrequencyThenName() {
        val entities = listOf(
            Entity("1", "דנה", EntityType.PERSON),
            Entity("2", "מיכל", EntityType.PERSON),
            Entity("3", "אורח", EntityType.PERSON, hidden = true),
        )
        val ordered = EntitySuggestions.forPicker("", entities, mapOf("2" to 4, "1" to 1))
        assertEquals(listOf("2", "1"), ordered.map { it.id })
    }

    @Test
    fun meetingSearch_includesForumMeetingsForAMember() {
        val dana = Entity("p1", "דנה", EntityType.PERSON)
        val forum = Entity("f1", "פורום בוקר", EntityType.FORUM, memberIds = listOf("p1"))
        val meetings = listOf(
            meeting("own", LocalDate.of(2026, 1, 1), "דנה").copy(entityIds = listOf("p1"), entityNames = listOf("דנה")),
            meeting("group", LocalDate.of(2026, 2, 1), "פורום בוקר").copy(entityIds = listOf("f1"), entityNames = listOf("פורום בוקר")),
            meeting("other", LocalDate.of(2026, 3, 1), "מיכל").copy(entityIds = listOf("p2"), entityNames = listOf("מיכל")),
        )
        val hits = MeetingSearch.search(meetings, listOf(dana, forum), "דנה")
        assertEquals(listOf("group", "own"), hits.map { it.meeting.id })
        assertEquals("פורום בוקר", hits.first().forumName)
        val forumHits = MeetingSearch.search(meetings, listOf(dana, forum), "בוקר")
        assertEquals(listOf("group"), forumHits.map { it.meeting.id })
        assertNull(forumHits.first().forumName)
    }

    @Test
    fun repeatWarning_matchesPersonForumAndSkipsHiddenAndSelf() {
        val roee = Entity("p1", "רועי", EntityType.PERSON)
        val forum = Entity("f1", "פורום", EntityType.FORUM, memberIds = listOf("p1"))
        val older = Meeting(
            id = "old",
            date = LocalDate.of(2026, 3, 1),
            entityIds = listOf("p1"),
            entityNames = listOf("רועי"),
            clothingItemIds = listOf("shirt"),
            photoUrl = null,
            note = "",
        )
        val newer = older.copy(id = "new", date = LocalDate.of(2026, 3, 12), entityIds = listOf("f1"), entityNames = listOf("פורום"))
        val hidden = newer.copy(id = "hid", date = LocalDate.of(2026, 4, 1), hidden = true)
        val self = newer.copy(id = "self", date = LocalDate.of(2026, 5, 1))
        val warning = RepeatWarnings.forSelection(
            selectedEntityIds = setOf("p1"),
            selectedItemIds = setOf("shirt"),
            entities = listOf(roee, forum),
            meetings = listOf(older, newer, hidden, self),
            excludeMeetingId = "self",
        ).getValue("shirt")
        assertEquals("פורום", warning.entityName)
        assertEquals(LocalDate.of(2026, 3, 12), warning.date)
        assertEquals(1, warning.extraCount)
        val none = RepeatWarnings.forSelection(
            selectedEntityIds = setOf("p1"),
            selectedItemIds = setOf("shirt"),
            entities = listOf(roee, forum),
            meetings = listOf(hidden),
        )
        assertTrue(none.isEmpty())
    }

    @Test
    fun meetingRead_keepsLegacySingleEntityAsAList() {
        val legacy = Meeting(
            id = "m",
            date = LocalDate.of(2026, 11, 4),
            entityIds = listOf("p1"),
            entityNames = listOf("דנה"),
            clothingItemIds = emptyList(),
            photoUrl = null,
            note = "",
        )
        assertEquals("p1", legacy.entityId)
        assertEquals("04-11-26", legacy.date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yy")))
        val hiddenFiltered = MeetingSearch.search(
            meetings = listOf(legacy.copy(hidden = true), legacy.copy(id = "visible")),
            entities = emptyList(),
            query = "",
            includeHidden = false,
        )
        assertEquals(listOf("visible"), hiddenFiltered.map { it.meeting.id })
    }
}
