package com.ember.reader.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoutesTest {

    @Test
    fun `a route with no optional arguments has no query`() {
        assertEquals("catalog/3", Routes.catalog(3))
        assertEquals("library/3", Routes.library(3))
    }

    @Test
    fun `the book list route carries its path and title`() {
        assertEquals(
            "library/3?path=grimmory%3AlibraryId%3D5&title=Science%20%26%20Nature",
            Routes.library(3, "grimmory:libraryId=5", "Science & Nature")
        )
    }

    @Test
    fun `a space is sent as percent-20, never as a plus sign`() {
        assertEquals("library/3?title=Recently%20Added", Routes.library(3, title = "Recently Added"))
        assertEquals("library/3?title=C%2B%2B", Routes.library(3, title = "C++"))
    }

    @Test
    fun `a missing argument is left out, not sent empty`() {
        assertEquals("library/3?title=Fiction", Routes.library(3, title = "Fiction"))
        assertEquals("library/3?path=p", Routes.library(3, "p"))
    }

    @Test
    fun `the catalogue route marks the root only when asked`() {
        assertEquals("catalog/3?root=true", Routes.catalog(3, root = true))
        assertEquals("catalog/3?path=grimmory%3Aseries", Routes.catalog(3, "grimmory:series"))
    }
}
