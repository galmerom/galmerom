package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SquareCropTest {
    @Test
    fun centerSquare_cropsTheLongerSide() {
        assertEquals(SquareCrop(100, 0, 400), centerSquareCrop(600, 400))
        assertEquals(SquareCrop(0, 150, 300), centerSquareCrop(300, 600))
        assertEquals(SquareCrop(0, 0, 200), centerSquareCrop(200, 200))
        assertNull(centerSquareCrop(0, 40))
    }
}
