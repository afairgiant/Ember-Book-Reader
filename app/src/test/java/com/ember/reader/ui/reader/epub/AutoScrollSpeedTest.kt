package com.ember.reader.ui.reader.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoScrollSpeedTest {

    @Test
    fun `the default is one of the steps`() {
        assertTrue(AutoScrollSpeed.DEFAULT in AutoScrollSpeed.STEPS)
    }

    @Test
    fun `stepping moves to the neighbouring speed and stays put at the ends`() {
        assertEquals(15f, AutoScrollSpeed.step(12f, faster = true))
        assertEquals(10f, AutoScrollSpeed.step(12f, faster = false))
        assertEquals(4f, AutoScrollSpeed.step(4f, faster = false))
        assertEquals(50f, AutoScrollSpeed.step(50f, faster = true))
    }

    @Test
    fun `a speed between two steps counts as the nearer one`() {
        assertEquals(AutoScrollSpeed.level(12f), AutoScrollSpeed.level(12.9f))
        assertEquals(1, AutoScrollSpeed.level(0f))
        assertEquals(AutoScrollSpeed.STEPS.size, AutoScrollSpeed.level(900f))
    }
}
