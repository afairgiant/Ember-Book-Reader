package com.ember.reader.ui.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeGreetingTest {

    @Test
    fun `the greeting follows the time of day`() {
        assertEquals("Good morning", greetingFor(5))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(17))
        assertEquals("Good evening", greetingFor(18))
        assertEquals("Good evening", greetingFor(23))
    }

    @Test
    fun `the small hours count as evening`() {
        assertEquals("Good evening", greetingFor(0))
        assertEquals("Good evening", greetingFor(4))
    }
}
