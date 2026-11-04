package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CallCountsTest {
    @Test
    fun sumsOutgoingAcrossEveryNumber_andIgnoresTheRest() {
        val now = 1_000_000L
        val since = now - 1_000
        val contacts = listOf(
            ContactNumbers("a", listOf("050-123-4567", "+972509998888", "050-123-4567")),
            ContactNumbers("b", listOf("03-555-1234")),
        )
        val calls = listOf(
            CallRecord("+972501234567", CallDirection.Outgoing, now - 10),
            CallRecord("0509998888", CallDirection.Outgoing, now - 20),
            CallRecord("035551234", CallDirection.Outgoing, now - 30),
            CallRecord("0501234567", CallDirection.Incoming, now - 5),
            CallRecord("0501234567", CallDirection.Missed, now - 5),
            CallRecord("0501234567", CallDirection.Outgoing, since - 1),
            CallRecord("0500000000", CallDirection.Outgoing, now - 5),
            CallRecord("*123", CallDirection.Outgoing, now - 5),
        )
        assertEquals(mapOf("a" to 2, "b" to 1), outgoingCallCounts(calls, contacts, since, now))
    }

    @Test
    fun windowIsInclusive_andADuplicateNumberCountsOnce() {
        val contacts = listOf(ContactNumbers("a", listOf("0501234567", "050-123-4567")))
        val at = 500L
        val calls = listOf(
            CallRecord("0501234567", CallDirection.Outgoing, at),
            CallRecord("+972501234567", CallDirection.Outgoing, at + 1),
        )
        assertEquals(mapOf("a" to 1), outgoingCallCounts(calls, contacts, at, at))
        assertEquals(emptyMap<String, Int>(), outgoingCallCounts(calls, contacts, at, at - 1))
        assertEquals(mapOf("a" to 2), outgoingCallCounts(calls, contacts, at, at + 1))
    }

    @Test
    fun sixMonths_usesTheLocalCalendar() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val now = ZonedDateTime.of(2026, 11, 4, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 5, 4, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, sixMonthsBefore(now, zone))
    }

    @Test
    fun phoneKeys_collapseIsraeliFormats() {
        assertEquals(phoneKey("+972 50-123-4567"), phoneKey("0501234567"))
        assertEquals(phoneKey("00972501234567"), phoneKey("050-123-4567"))
        assertEquals(phoneKey("03-555-1234"), phoneKey("+97235551234"))
        assertEquals(true, samePhone("050-123-4567", "+972501234567"))
        assertEquals(false, samePhone("050-123-4567", "050-123-4568"))
        assertEquals(false, samePhone("*123", "123"))
    }

    @Test
    fun whatsAppDigits_andDialableNumbers() {
        assertEquals("972501234567", whatsAppDigits("972501234567@s.whatsapp.net"))
        assertEquals("972501234567", whatsAppDigits("050-123-4567"))
        assertEquals("97235551234", whatsAppDigits("+972 3-555-1234"))
        assertNull(whatsAppDigits("   "))
        assertEquals("+972501234567", dialableNumber(" +972 (50) 123-4567 "))
        assertEquals("*123#", dialableNumber(" *123# "))
    }

    @Test
    fun suggestedNumber_prefersSuperPrimaryThenPrimaryThenMobile() {
        val options = listOf(
            NumberOption("0500000001", mobile = true),
            NumberOption("0500000002", primary = true),
            NumberOption("0500000003", superPrimary = true),
        )
        assertEquals(2, suggestedIndex(options))
        assertEquals(1, suggestedIndex(options.dropLast(1)))
        assertEquals(1, suggestedIndex(listOf(NumberOption("0500000009"), NumberOption("030000000", mobile = true))))
        assertEquals(0, suggestedIndex(listOf(NumberOption("0500000009"), NumberOption("030000000"))))
        assertEquals(-1, suggestedIndex(emptyList()))

        val duplicated = distinctNumbers(
            listOf(
                NumberOption("050-123-4567"),
                NumberOption("+972501234567", superPrimary = true),
                NumberOption("035551234", mobile = false),
            ),
        )
        assertEquals(listOf("+972501234567", "035551234"), duplicated.map { it.number })
    }
}
