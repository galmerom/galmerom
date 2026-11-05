package com.galmerom.quickdial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class SpeechWindowTest {
    @Test
    fun silenceAfterSpeechStops_andNoSpeechWaitsForTheCap() {
        assertTrue(SpeechWindow.shouldStop(2_000, speechStarted = true, silenceSinceSpeechMs = 2_000))
        assertEquals(false, SpeechWindow.shouldStop(1_500, speechStarted = true, silenceSinceSpeechMs = 1_000))
        assertEquals(false, SpeechWindow.shouldStop(2_000, speechStarted = false, silenceSinceSpeechMs = null))
        assertTrue(SpeechWindow.shouldStop(5_000, speechStarted = false, silenceSinceSpeechMs = null))
        assertEquals(2_000L, SpeechWindow.SILENCE_AFTER_SPEECH_MS)
        assertEquals(5_000L, SpeechWindow.MAX_LISTEN_MS)
    }
}
