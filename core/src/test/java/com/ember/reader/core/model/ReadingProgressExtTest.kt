package com.ember.reader.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingProgressExtTest {

    @Test
    fun `toGrimmoryPercentage converts 0-1 to 0-100 with one decimal`() {
        assertEquals(50.0f, 0.5f.toGrimmoryPercentage())
        assertEquals(12.3f, 0.123f.toGrimmoryPercentage())
        assertEquals(99.9f, 0.999f.toGrimmoryPercentage())
        assertEquals(75.0f, 0.75f.toGrimmoryPercentage())
    }

    @Test
    fun `toGrimmoryPercentage handles boundary values`() {
        assertEquals(0.0f, 0f.toGrimmoryPercentage())
        assertEquals(100.0f, 1f.toGrimmoryPercentage())
    }

    @Test
    fun `normalizeGrimmoryPercentage converts 0-100 to 0-1`() {
        assertEquals(0.75f, 75f.normalizeGrimmoryPercentage())
        assertEquals(0.5f, 50f.normalizeGrimmoryPercentage())
        assertEquals(1.0f, 100f.normalizeGrimmoryPercentage())
    }

    @Test
    fun `normalizeGrimmoryPercentage leaves 0-1 values unchanged`() {
        assertEquals(0.75f, 0.75f.normalizeGrimmoryPercentage())
        assertEquals(0.5f, 0.5f.normalizeGrimmoryPercentage())
        assertEquals(0f, 0f.normalizeGrimmoryPercentage())
        // 1f is ambiguous (could be 1% in 0-100 or 100% in 0-1) — current logic leaves it as 1f
        assertEquals(1f, 1f.normalizeGrimmoryPercentage())
    }
}
