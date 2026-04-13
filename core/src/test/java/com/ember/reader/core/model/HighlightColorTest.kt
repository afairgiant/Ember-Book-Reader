package com.ember.reader.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HighlightColorTest {

    @Test
    fun `fromHex maps Ember hex codes`() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("#FFEB3B"))
        assertEquals(HighlightColor.GREEN, HighlightColor.fromHex("#4CAF50"))
        assertEquals(HighlightColor.BLUE, HighlightColor.fromHex("#2196F3"))
        assertEquals(HighlightColor.PINK, HighlightColor.fromHex("#E91E63"))
        assertEquals(HighlightColor.ORANGE, HighlightColor.fromHex("#FF9800"))
        assertEquals(HighlightColor.PURPLE, HighlightColor.fromHex("#9C27B0"))
    }

    @Test
    fun `fromHex maps Grimmory hex codes`() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("FACC15"))
        assertEquals(HighlightColor.GREEN, HighlightColor.fromHex("4ADE80"))
        assertEquals(HighlightColor.BLUE, HighlightColor.fromHex("38BDF8"))
        assertEquals(HighlightColor.PINK, HighlightColor.fromHex("F472B6"))
        assertEquals(HighlightColor.ORANGE, HighlightColor.fromHex("FB923C"))
    }

    @Test
    fun `fromHex is case-insensitive`() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("ffeb3b"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("FFEB3B"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("facc15"))
        assertEquals(HighlightColor.GREEN, HighlightColor.fromHex("4ade80"))
    }

    @Test
    fun `fromHex strips hash prefix`() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("#FACC15"))
        assertEquals(HighlightColor.GREEN, HighlightColor.fromHex("#4ADE80"))
    }

    @Test
    fun `fromHex returns YELLOW for null`() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex(null))
    }

    @Test
    fun `fromHex returns YELLOW for unknown hex`() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("ABCDEF"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex("000000"))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromHex(""))
    }
}
