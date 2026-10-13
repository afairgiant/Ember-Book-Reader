package com.ember.reader.ui.reader.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpacingSettingTest {

    @Test
    fun `the slider has a stop for every step between its ends`() {
        assertEquals(9, WordSpacing.steps)
        assertEquals(19, ParagraphSpacing.steps)
        assertEquals(14, ParagraphIndent.steps)
    }

    @Test
    fun `a dragged value snaps to the nearest stop`() {
        assertEquals(0.3f, WordSpacing.snap(0.31f), 0.0001f)
        assertEquals(1.2f, ParagraphIndent.snap(1.27f), 0.0001f)
        assertEquals(1.4f, ParagraphIndent.snap(1.31f), 0.0001f)
    }

    @Test
    fun `a value outside the range is brought back inside it`() {
        assertEquals(1f, LetterSpacing.snap(1.4f), 0.0001f)
        assertEquals(0f, LetterSpacing.snap(-0.2f), 0.0001f)
    }

    @Test
    fun `an unset paragraph setting reads as the book's default`() {
        assertEquals("Book default", ParagraphSpacing.valueLabel(null))
        assertEquals("None", ParagraphIndent.valueLabel(0f))
        assertEquals("1.2", ParagraphIndent.valueLabel(1.2f))
    }

    @Test
    fun `word and letter spacing read as normal at zero`() {
        assertEquals("Normal", WordSpacing.valueLabel(0f))
        assertEquals("0.3", LetterSpacing.valueLabel(0.3f))
    }
}
