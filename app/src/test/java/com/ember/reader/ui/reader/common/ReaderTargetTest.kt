package com.ember.reader.ui.reader.common

import com.ember.reader.navigation.Routes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaderTargetTest {

    @Test
    fun `a bookmark and a highlight come back from their mark argument`() {
        listOf(ReaderTarget.Bookmark(12), ReaderTarget.Highlight(7)).forEach { target ->
            assertEquals(target, ReaderTarget.fromArguments(mark = target.markArgument, cfi = null))
        }
    }

    @Test
    fun `a cfi is its own argument`() {
        val target = ReaderTarget.Cfi("epubcfi(/6/14!/4/2/1:0)")

        assertNull(target.markArgument)
        assertEquals(target, ReaderTarget.fromArguments(mark = null, cfi = target.cfi))
    }

    @Test
    fun `no arguments or a mark that names nothing is no target`() {
        assertNull(ReaderTarget.fromArguments(mark = null, cfi = null))
        assertNull(ReaderTarget.fromArguments(mark = "bookmark-x", cfi = ""))
        assertNull(ReaderTarget.fromArguments(mark = "grimmory-NOTE-3", cfi = null))
    }

    @Test
    fun `the reader route carries the target and stays as it was without one`() {
        assertEquals("reader/epub/b1", Routes.epubReader("b1"))
        assertEquals("reader/epub/b1?mode=stream", Routes.epubReader("b1", streaming = true))
        assertEquals("reader/pdf/b1?mark=bookmark-12", Routes.pdfReader("b1", target = ReaderTarget.Bookmark(12)))
        assertEquals(
            "reader/epub/b1?cfi=epubcfi%28%2F6%2F14%21%2F4%2F2%2F1%3A0%29",
            Routes.epubReader("b1", target = ReaderTarget.Cfi("epubcfi(/6/14!/4/2/1:0)"))
        )
    }
}
