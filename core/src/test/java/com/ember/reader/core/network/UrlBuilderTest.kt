package com.ember.reader.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlBuilderTest {

    // --- normalizeUrl ---

    @Test
    fun `normalizeUrl adds http scheme when missing`() {
        assertEquals("http://example.com", normalizeUrl("example.com"))
    }

    @Test
    fun `normalizeUrl preserves existing https scheme`() {
        assertEquals("https://example.com", normalizeUrl("https://example.com"))
    }

    @Test
    fun `normalizeUrl trims trailing slash`() {
        assertEquals("https://example.com", normalizeUrl("https://example.com/"))
    }

    // --- serverOrigin ---

    @Test
    fun `serverOrigin extracts origin from full URL`() {
        assertEquals(
            "http://192.168.0.174:6060",
            serverOrigin("http://192.168.0.174:6060/api/v1/opds")
        )
    }

    @Test
    fun `serverOrigin returns URL when no path`() {
        assertEquals(
            "https://example.com",
            serverOrigin("https://example.com")
        )
    }

    // --- resolveUrl ---

    @Test
    fun `resolveUrl returns absolute http href as-is`() {
        val absolute = "http://other.example.com/resource"
        assertEquals(absolute, resolveUrl("https://example.com", absolute))
    }

    @Test
    fun `resolveUrl returns absolute https href as-is`() {
        val absolute = "https://cdn.example.com/image.jpg"
        assertEquals(absolute, resolveUrl("https://example.com", absolute))
    }

    @Test
    fun `resolveUrl resolves absolute path against origin`() {
        assertEquals(
            "https://example.com/api/books",
            resolveUrl("https://example.com/api/v1/opds", "/api/books")
        )
    }

    @Test
    fun `resolveUrl resolves relative path against base`() {
        assertEquals(
            "https://example.com/api/v1/opds/catalog",
            resolveUrl("https://example.com/api/v1/opds", "catalog")
        )
    }

    @Test
    fun `resolveUrl resolves with IP and port`() {
        assertEquals(
            "http://192.168.0.174:6060/api/koreader/syncs",
            resolveUrl("http://192.168.0.174:6060/api/v1/opds", "/api/koreader/syncs")
        )
    }
}
