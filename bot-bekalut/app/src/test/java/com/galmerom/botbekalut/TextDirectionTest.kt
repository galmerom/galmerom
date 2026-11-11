package com.galmerom.botbekalut

import com.galmerom.botbekalut.domain.BlockDirection
import com.galmerom.botbekalut.domain.blockDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDirectionTest {
    @Test
    fun hebrewIsRtl() {
        assertEquals(BlockDirection.Rtl, blockDirection("שלום עולם"))
        assertEquals(BlockDirection.Rtl, blockDirection("مرحبا"))
    }

    @Test
    fun englishIsLtr() {
        assertEquals(BlockDirection.Ltr, blockDirection("Hello from Ilan"))
    }

    @Test
    fun emojiFirstUsesTheFollowingWord() {
        assertEquals(BlockDirection.Ltr, blockDirection("🚀 Hello"))
        assertEquals(BlockDirection.Rtl, blockDirection("🔥 שלום"))
    }

    @Test
    fun numberFirstUsesTheFollowingWord() {
        assertEquals(BlockDirection.Ltr, blockDirection("2026. Tesla rose"))
        assertEquals(BlockDirection.Rtl, blockDirection("3) עדכון בוקר"))
    }

    @Test
    fun markdownListPrefixIsSkipped() {
        assertEquals(BlockDirection.Ltr, blockDirection("- KORU update"))
        assertEquals(BlockDirection.Ltr, blockDirection("* Market note"))
        assertEquals(BlockDirection.Rtl, blockDirection("- סיכום X"))
        assertEquals(BlockDirection.Rtl, blockDirection("# כותרת"))
    }

    @Test
    fun urlFirstIsSkippedUntilRealText() {
        assertEquals(BlockDirection.Ltr, blockDirection("https://example.com/news"))
        assertEquals(BlockDirection.Ltr, blockDirection("https://x.com/elonmusk/status/1 more"))
        assertEquals(BlockDirection.Rtl, blockDirection("https://x.com/elonmusk עדכון שלא נכנס"))
        assertEquals(BlockDirection.Rtl, blockDirection("[פוסט](https://example.com/a)"))
    }
}
