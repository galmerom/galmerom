package com.galmerom.botbekalut

import com.galmerom.botbekalut.domain.ChatMessage
import com.galmerom.botbekalut.domain.ChatRow
import com.galmerom.botbekalut.domain.DEFAULT_BOT_ORDER
import com.galmerom.botbekalut.domain.buildChatRows
import com.galmerom.botbekalut.domain.fallbackColor
import com.galmerom.botbekalut.domain.inDisplayWindow
import com.galmerom.botbekalut.domain.isXLink
import com.galmerom.botbekalut.domain.prepareMessages
import com.galmerom.botbekalut.domain.visibleBots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class BotsTest {
    private val now = 1_700_000_000_000L

    @Test
    fun defaultOrderAndUnreadDotData() {
        val messages = listOf(
            message("g", "groki", "גרוקי", now - 1_000, "#EF6C00"),
            message("a", "analyst", "האנליסט", now - 2_000, null),
            message("i", "ilan", "אילן", now - 3_000, "#1565C0"),
            message("old", "ayelet", "איילת", now - 25L * 60 * 60 * 1000, null),
        )
        val bots = visibleBots(messages, DEFAULT_BOT_ORDER, readCursors = mapOf("ilan" to now), now = now)
        assertEquals(listOf("ilan", "analyst", "groki"), bots.map { it.id })
        assertFalse(bots.first { it.id == "ilan" }.hasUnread)
        assertTrue(bots.first { it.id == "analyst" }.hasUnread)
        assertEquals(0x2E7D32, bots.first { it.id == "analyst" }.colorRgb)
    }

    @Test
    fun unknownBotUsesStableColorAndAppears() {
        val first = fallbackColor("sharon")
        assertEquals(first, fallbackColor("sharon"))
        assertTrue(first != fallbackColor("ilan"))
        val bots = visibleBots(
            listOf(message("s", "sharon", "שרון", now - 500, null)),
            DEFAULT_BOT_ORDER,
            emptyMap(),
            now,
        )
        assertEquals("sharon", bots.single().id)
        assertEquals("שרון", bots.single().name)
        assertEquals(first, bots.single().colorRgb)
    }

    @Test
    fun windowIsStrictlyGreaterThan24Hours() {
        val cutoff = now - 24L * 60 * 60 * 1000
        assertFalse(inDisplayWindow(cutoff, now))
        assertTrue(inDisplayWindow(cutoff + 1, now))
        val kept = prepareMessages(
            listOf(
                message("new", "ilan", "אילן", cutoff + 5, null),
                message("edge", "ilan", "אילן", cutoff, null),
                message("dup", "ilan", "אילן", cutoff + 5, null, source = "same"),
                message("dup2", "ilan", "אילן", cutoff + 6, null, source = "same"),
            ),
            now,
        )
        assertEquals(listOf("new", "dup2"), kept.map { it.id })
    }

    @Test
    fun xLinks() {
        assertTrue(isXLink("https://x.com/elonmusk"))
        assertTrue(isXLink("https://www.twitter.com/home"))
        assertTrue(isXLink("https://mobile.x.com/i"))
        assertFalse(isXLink("https://example.com/x.com"))
    }

    @Test
    fun stickyRoutineHeadersFollowConsecutiveRuns() {
        val rows = buildChatRows(
            listOf(
                message("1", "ilan", "אילן", now, null, routine = "a", routineName = "בוקר"),
                message("2", "ilan", "אילן", now + 1, null, routine = "a", routineName = "בוקר"),
                message("3", "ilan", "אילן", now + 2, null, routine = "b", routineName = "ערב"),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(2, rows.count { it is ChatRow.Routine })
        assertEquals(listOf("בוקר", "ערב"), rows.filterIsInstance<ChatRow.Routine>().map { it.title })
    }

    private fun message(
        id: String,
        botId: String,
        name: String,
        publishedAt: Long,
        color: String?,
        routine: String = "routine",
        routineName: String = "שגרה",
        source: String? = null,
    ) = ChatMessage(
        id = id,
        botId = botId,
        botName = name,
        botColor = color,
        routineId = routine,
        routineName = routineName,
        publishedAt = publishedAt,
        content = "תוכן",
        sourceMessageId = source,
    )
}
