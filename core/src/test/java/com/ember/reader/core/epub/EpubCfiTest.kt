package com.ember.reader.core.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpubCfiTest {

    @Test
    fun `isRealCfi accepts point and range CFIs`() {
        assertTrue(EpubCfi.isRealCfi("epubcfi(/6/4!/4/2/1:10)"))
        assertTrue(EpubCfi.isRealCfi("epubcfi(/6/112[chap56]!/4/2[body01]/10,/1:0,/1:100)"))
    }

    @Test
    fun `isRealCfi rejects the values Ember has used in place of a CFI`() {
        assertFalse(EpubCfi.isRealCfi("epubcfi(/6/2)"))
        assertFalse(EpubCfi.isRealCfi("0.4231"))
        assertFalse(EpubCfi.isRealCfi(""))
        assertFalse(EpubCfi.isRealCfi(null))
    }

    @Test
    fun `isRealCfi rejects an unwrapped CFI, an odd itemref step and an empty content path`() {
        assertFalse(EpubCfi.isRealCfi("/6/4!/4/2/1:10"))
        assertFalse(EpubCfi.isRealCfi("epubcfi(/6/3!/4/2)"))
        assertFalse(EpubCfi.isRealCfi("epubcfi(/6/4!)"))
    }

    @Test
    fun `unwrap strips repeated wrapping and wrap adds exactly one`() {
        assertEquals("/6/4!/4", EpubCfi.unwrap("epubcfi(epubcfi(/6/4!/4))"))
        assertEquals("epubcfi(/6/4!/4)", EpubCfi.wrap("epubcfi(epubcfi(/6/4!/4))"))
        assertEquals("epubcfi(/6/4!/4)", EpubCfi.wrap("/6/4!/4"))
    }

    @Test
    fun `split separates base and content and join puts them back`() {
        val parts = EpubCfi.split("epubcfi(/6/4[chap1]!/4/2/1:10)")

        assertEquals(EpubCfi.Parts(base = "/6/4[chap1]", content = "/4/2/1:10"), parts)
        assertEquals("epubcfi(/6/4[chap1]!/4/2/1:10)", EpubCfi.join(parts!!.base, parts.content))
    }

    @Test
    fun `split returns null without an indirection step`() {
        assertNull(EpubCfi.split("epubcfi(/6/2)"))
    }

    @Test
    fun `split ignores a bang inside an id`() {
        assertEquals(
            EpubCfi.Parts(base = "/6/4[wow!]", content = "/4"),
            EpubCfi.split("epubcfi(/6/4[wow!]!/4)"),
        )
    }

    @Test
    fun `steps reads indexes and ids, including escaped characters`() {
        assertEquals(
            listOf(EpubCfi.Step(6), EpubCfi.Step(4, "chap1")),
            EpubCfi.steps("/6/4[chap1]"),
        )
        assertEquals(listOf(EpubCfi.Step(4, "a]b")), EpubCfi.steps("/4[a^]b]"))
    }

    @Test
    fun `steps returns null for something that is not a path`() {
        assertNull(EpubCfi.steps("0.4231"))
        assertNull(EpubCfi.steps("/6/x"))
        assertNull(EpubCfi.steps("/6/4[open"))
    }

    @Test
    fun `formatStep escapes reserved characters so steps can read them back`() {
        val step = EpubCfi.Step(4, "a]b[c")

        val formatted = EpubCfi.formatStep(step)

        assertEquals("/4[a^]b^[c]", formatted)
        assertEquals(listOf(step), EpubCfi.steps(formatted))
    }
}
