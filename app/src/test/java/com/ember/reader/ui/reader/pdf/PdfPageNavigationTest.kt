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
}
