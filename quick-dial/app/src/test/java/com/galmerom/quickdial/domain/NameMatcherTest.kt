package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {
    private val contacts = listOf(
        NamedContact("1", "דוד כהן", "דוד"),
        NamedContact("2", "דנה לוי", "דנה"),
        NamedContact("3", "יוסף אברהם", "יוסי"),
        NamedContact("4", "David Cohen", "Dave"),
    )

    @Test
    fun exactFullName_isConfident() {
        assertEquals(NameMatch.One("1"), matchSpokenName("דוד כהן", contacts))
    }

    @Test
    fun exactShortName_isConfident() {
        assertEquals(NameMatch.One("3"), matchSpokenName("יוסי", contacts))
    }

    @Test
    fun sharedToken_listsEveryMatch_sortedByName() {
        val people = listOf(
            NamedContact("b", "דוד לוי", "דודי"),
            NamedContact("a", "דוד כהן", "דוד"),
        )
        assertEquals(NameMatch.Many(listOf("a", "b")), matchSpokenName("דוד", people))
    }

    @Test
    fun uniqueFirstName_callsThatPerson() {
        assertEquals(NameMatch.One("2"), matchSpokenName("דנה", contacts))
    }

    @Test
    fun niqqudAndFinalLetters_matchThePlainName() {
        assertEquals(NameMatch.One("3"), matchSpokenName("יוֹסֵף", contacts))
        val spoon = listOf(NamedContact("s", "סוף שבוע", "סוף"))
        assertEquals(NameMatch.One("s"), matchSpokenName("סופ", spoon))
    }

    @Test
    fun punctuationCaseAndMaqaf_areIgnored() {
        assertEquals(NameMatch.One("4"), matchSpokenName("  DAVID   cohen ", contacts))
        val joined = listOf(NamedContact("1", "דוד כהן", "דוד"))
        assertEquals(NameMatch.One("1"), matchSpokenName("דוד־כהן", joined))
        val john = listOf(NamedContact("j", "ג'ון", "ג'ון"))
        assertEquals(NameMatch.One("j"), matchSpokenName("גון", john))
    }

    @Test
    fun reversedWords_stillMatchTheFullName() {
        assertEquals(NameMatch.One("1"), matchSpokenName("כהן דוד", contacts))
    }

    @Test
    fun callPhraseAndFillers_areStripped() {
        assertEquals(NameMatch.One("1"), matchSpokenName("התקשר אל דוד כהן", contacts))
        assertEquals(NameMatch.One("2"), matchSpokenName("תתקשר לדנה בבקשה", contacts))
        assertEquals(NameMatch.One("4"), matchSpokenName("call David", contacts))
        assertEquals(NameMatch.One("3"), matchSpokenName("אני רוצה יוסי", contacts))
    }

    @Test
    fun noMatchOrEmpty_doesNotCall() {
        assertEquals(NameMatch.None, matchSpokenName("xyzqwerty", contacts))
        assertEquals(NameMatch.None, matchSpokenName("   ", contacts))
        assertEquals(NameMatch.None, matchSpokenName("דוד", emptyList()))
        assertEquals(NameMatch.None, matchSpokenName("התקשר אל", contacts))
    }

    @Test
    fun shortNames_doNotFuzzyMatchEachOther() {
        val similar = listOf(
            NamedContact("a", "דנה", "דנה"),
            NamedContact("b", "דני", "דני"),
        )
        assertEquals(NameMatch.One("a"), matchSpokenName("דנה", similar))
        assertEquals(NameMatch.None, matchSpokenName("דנו", similar))
    }

    @Test
    fun oneEditOnALongerName_isConfidentWhenUnique() {
        val people = listOf(NamedContact("a", "אלכסנדר", "אלכס"))
        assertEquals(NameMatch.One("a"), matchSpokenName("אלקסנדר", people))
    }

    @Test
    fun twoCloseLongNames_askInsteadOfGuessing() {
        val people = listOf(
            NamedContact("a", "שלומית", "שלומית"),
            NamedContact("b", "שלומים", "שלומים"),
        )
        assertEquals(NameMatch.Many(listOf("b", "a")), matchSpokenName("שלומיח", people))
    }

    @Test
    fun uniquePrefix_calls_andSharedPrefix_asks() {
        assertEquals(NameMatch.One("3"), matchSpokenName("אבר", listOf(contacts[2])))
        val both = listOf(
            NamedContact("a", "אברהם כהן", "אברהם"),
            NamedContact("b", "אברם לוי", "אברם"),
        )
        assertEquals(NameMatch.Many(listOf("a", "b")), matchSpokenName("אבר", both))
    }

    @Test
    fun tooBroadPrefix_isNotSure() {
        val people = (1..9).map { NamedContact("p$it", "אבר$it שם", "אבר$it") }
        assertEquals(NameMatch.None, matchSpokenName("אבר", people))
    }

    @Test
    fun hypotheses_useTheFirstDecisiveGuess() {
        assertEquals(
            NameMatch.One("2"),
            matchHypotheses(listOf("לא ידוע", "דנה"), contacts),
        )
        assertEquals(
            NameMatch.Many(listOf("a", "b")),
            matchHypotheses(
                listOf("דוד", "דנה"),
                listOf(
                    NamedContact("a", "דוד כהן", "דוד"),
                    NamedContact("b", "דוד לוי", "דוד"),
                ),
            ),
        )
        assertEquals(NameMatch.None, matchHypotheses(listOf("", "   "), contacts))
    }

    @Test
    fun normalize_stripsNiqqudAndMapsFinalLetters() {
        assertEquals("יוספ", normalizeName("יוֹסֵף"))
        assertEquals("סופ", normalizeName("סוֹף"))
        assertEquals("דוד כהנ", normalizeName("  דוד   כהן "))
        assertTrue(matchesContactQuery("דנה לוי", listOf("050-1234567"), "דנה"))
        assertTrue(matchesContactQuery("דנה לוי", listOf("050-1234567"), "1234"))
        assertEquals(false, matchesContactQuery("דנה לוי", listOf("050-1234567"), "מיכל"))
    }

    @Test
    fun shortNameHelpers_fallBackToTheFirstWord() {
        assertEquals("דוד", defaultShortName("  דוד   כהן "))
        assertEquals("יוסי", cleanShortName("  ", "יוסי אברהם"))
        assertEquals("מאיה", cleanShortName("מאיה", "מאיה לוי"))
        assertEquals("?", cleanShortName("  ", "   "))
        assertEquals("ד", initialLetter(" דנה "))
        assertEquals("?", initialLetter(" "))
        assertEquals(0, avatarIndex("א", 0))
        assertTrue(avatarIndex("דנה", 8) in 0..7)
    }
}
