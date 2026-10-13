package com.ember.reader.ui.settings.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatTextTest {

    @Test
    fun `reading within one day shows the day and its hours`() {
        assertEquals(
            "Oct 12 · 18:24–19:05",
            timelineSpan("2026-10-12T18:24:00", "2026-10-12T19:05:51")
        )
    }

    @Test
    fun `reading across days shows the two days`() {
        assertEquals("Oct 12 – Oct 14", timelineSpan("2026-10-12T18:24:00", "2026-10-14T07:00:00"))
    }

    @Test
    fun `a timestamp that cannot be read is shown as it came`() {
        assertEquals("soon – 2026-10-14T07:00:00", timelineSpan("soon", "2026-10-14T07:00:00"))
    }

    @Test
    fun `a count of one is singular`() {
        assertEquals("1 book", countOf(1, "book"))
        assertEquals("0 sessions", countOf(0, "session"))
        assertEquals("3 sessions", countOf(3, "session"))
    }
}
