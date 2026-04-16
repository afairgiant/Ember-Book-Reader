package com.ember.reader.core.paging

import com.ember.reader.core.grimmory.GrimmoryFilter
import com.ember.reader.core.grimmory.GrimmorySortKey
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.grimmory.SortDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrimmoryRequestTest {

    @Test
    fun `parses library and shelf ids from catalog path`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:libraryId=2&shelfId=5",
            GrimmoryFilter()
        )
        assertEquals(2L, req.libraryId)
        assertEquals(5L, req.shelfId)
    }

    @Test
    fun `decodes series name with url-encoded spaces`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:seriesName=Harry%20Potter",
            GrimmoryFilter()
        )
        assertEquals("Harry Potter", req.seriesName)
    }

    @Test
    fun `path status wins over filter status`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:status=READING",
            GrimmoryFilter(status = ReadStatus.UNREAD)
        )
        assertEquals("READING", req.status)
    }

    @Test
    fun `filter status applied when path omits status`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:",
            GrimmoryFilter(status = ReadStatus.READ)
        )
        assertEquals("READ", req.status)
    }

    @Test
    fun `filter sort and direction drive sort params`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:libraryId=1",
            GrimmoryFilter(sort = GrimmorySortKey.TITLE, direction = SortDirection.DESC)
        )
        assertEquals("title", req.sort)
        assertEquals("desc", req.dir)
    }

    @Test
    fun `search override replaces path search when non-blank`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:search=foo",
            GrimmoryFilter(),
            searchOverride = "bar"
        )
        assertEquals("bar", req.search)
    }

    @Test
    fun `blank search override keeps path search`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:search=foo",
            GrimmoryFilter(),
            searchOverride = ""
        )
        assertEquals("foo", req.search)
    }

    @Test
    fun `carries filter rating and language through`() {
        val req = GrimmoryRequest.fromCatalogPath(
            "grimmory:",
            GrimmoryFilter(
                minRating = 3,
                maxRating = 5,
                authors = "Tolkien",
                language = "en"
            )
        )
        assertEquals(3, req.minRating)
        assertEquals(5, req.maxRating)
        assertEquals("Tolkien", req.authors)
        assertEquals("en", req.language)
    }

    @Test
    fun `empty path yields defaults`() {
        val req = GrimmoryRequest.fromCatalogPath("grimmory:", GrimmoryFilter())
        assertNull(req.libraryId)
        assertNull(req.seriesName)
        assertNull(req.search)
        assertFalse(req.recentlyAdded)
    }

    @Test
    fun `recentlyAdded flag path sets recentlyAdded true`() {
        val req = GrimmoryRequest.fromCatalogPath("grimmory:recentlyAdded", GrimmoryFilter())
        assertTrue(req.recentlyAdded)
    }

    @Test
    fun `other paths leave recentlyAdded false`() {
        val req = GrimmoryRequest.fromCatalogPath("grimmory:shelfId=1", GrimmoryFilter())
        assertFalse(req.recentlyAdded)
    }
}
