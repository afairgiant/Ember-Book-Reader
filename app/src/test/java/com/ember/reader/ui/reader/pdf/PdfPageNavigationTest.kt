package com.ember.reader.ui.reader.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PdfPageNavigationTest {

    @Test
    fun `a percentage gives back the page it was saved from`() {
        assertEquals(1, pdfPageAt(0f, pageCount = 232))
        assertEquals(10, pdfPageAt(9f / 231f, pageCount = 232))
        assertEquals(232, pdfPageAt(1f, pageCount = 232))
        assertEquals(1, pdfPageAt(0.5f, pageCount = 1))
    }

    @Test
    fun `a page is opened with the position after it`() {
        assertEquals(2, positionOpeningPdfPage(page = 1, pageCount = 232))
        assertEquals(101, positionOpeningPdfPage(page = 100, pageCount = 232))
    }

    @Test
    fun `the last page and anything past it open the last position there is`() {
        assertEquals(232, positionOpeningPdfPage(page = 232, pageCount = 232))
        assertEquals(232, positionOpeningPdfPage(page = 500, pageCount = 232))
        assertNull(positionOpeningPdfPage(page = 3, pageCount = 0))
    }

    @Test
    fun `the page shown comes from the progression, and the last page from its start on`() {
        assertEquals(1, pdfPageShown(0.0, pageCount = 232))
        assertEquals(200, pdfPageShown(200.0 / 232, pageCount = 232))
        assertEquals(232, pdfPageShown(231.0 / 232, pageCount = 232))
        assertEquals(1, pdfPageShown(0.4, pageCount = 1))
    }

    @Test
    fun `a contents entry gives its page from the page fragment`() {
        assertEquals(12, pdfPageOfFragments(listOf("page=12")))
        assertEquals(7, pdfPageOfFragments(listOf("zoom=100", "page=7&view=Fit")))
        assertNull(pdfPageOfFragments(listOf("chapter-2")))
        assertNull(pdfPageOfFragments(listOf("page=0")))
        assertNull(pdfPageOfFragments(emptyList()))
    }
}
