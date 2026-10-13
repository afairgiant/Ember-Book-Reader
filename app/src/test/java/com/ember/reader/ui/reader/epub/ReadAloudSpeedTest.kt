package com.ember.reader.ui.reader.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadAloudSpeedTest {

    @Test
    fun `the speed steps up and down by a quarter`() {
        assertEquals(1.25, ReadAloudSpeed.step(1.0, faster = true))
        assertEquals(0.75, ReadAloudSpeed.step(1.0, faster = false))
        assertEquals(1.75, ReadAloudSpeed.step(2.0, faster = false))
    }

    @Test
    fun `nothing happens at either end of the range`() {
        assertEquals(2.0, ReadAloudSpeed.step(2.0, faster = true))
        assertEquals(0.75, ReadAloudSpeed.step(0.75, faster = false))
        assertTrue(ReadAloudSpeed.isFastest(2.0))
        assertTrue(ReadAloudSpeed.isSlowest(0.75))
        assertFalse(ReadAloudSpeed.isFastest(1.0))
        assertFalse(ReadAloudSpeed.isSlowest(1.0))
    }

    @Test
    fun `the label has no more decimals than the speed needs`() {
        assertEquals("1\u00D7", ReadAloudSpeed.label(1.0))
        assertEquals("1.25\u00D7", ReadAloudSpeed.label(1.25))
        assertEquals("0.75\u00D7", ReadAloudSpeed.label(0.75))
        assertEquals("2\u00D7", ReadAloudSpeed.label(2.0))
    }
}
