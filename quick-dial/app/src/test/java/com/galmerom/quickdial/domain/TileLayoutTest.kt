package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutTest {
    @Test
    fun ratio_clampsToTheSliderRange_andRepairsNaN() {
        assertEquals(TILE_RATIO_DEFAULT, tileRatio(Float.NaN))
        assertEquals(TILE_RATIO_MIN, tileRatio(0.1f))
        assertEquals(TILE_RATIO_MAX, tileRatio(3f))
        assertEquals(1.2f, tileRatio(1.2f))
        assertEquals(TILE_RATIO_DEFAULT, QuickDialSettings().tileRatio)
    }

    @Test
    fun tileHeight_neverShrinksTheSquarePhotoToMakeRoomForTheCaption() {
        val width = 100f
        val caption = 40f
        assertEquals(140f, tileHeightDp(width, 0.8f, caption), 0.01f)
        assertEquals(140f, tileHeightDp(width, TILE_RATIO_DEFAULT, caption), 0.01f)
        assertEquals(160f, tileHeightDp(width, 1.6f, caption), 0.01f)
    }

    @Test
    fun defaultRatio_keepsTheExtraHeightWhenTheCaptionFits() {
        val width = 200f
        val caption = 20f
        assertEquals(width * TILE_RATIO_DEFAULT, tileHeightDp(width, TILE_RATIO_DEFAULT, caption), 0.01f)
        assertTrue(caption <= width * (TILE_RATIO_DEFAULT - 1f))
    }

    @Test
    fun shortNameFont_growsWithTheTile_andWithTheSizeSetting() {
        val atReference = shortNameFontSp(20, REFERENCE_TILE_WIDTH_DP)
        val twiceAsWide = shortNameFontSp(20, REFERENCE_TILE_WIDTH_DP * 2f)
        assertEquals(20f, atReference, 0.01f)
        assertEquals(40f, twiceAsWide, 0.01f)
        assertTrue(shortNameFontSp(26, 180f) > shortNameFontSp(16, 180f))
        assertTrue(shortNameFontSp(20, 180f) > shortNameFontSp(20, 90f))
        assertTrue(letterFontSp(180f) > letterFontSp(90f))
    }
}