package com.ember.reader.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlBuilderTest {

    // --- buildServerUrl ---

    @Test
    fun `buildServerUrl joins base and path`() {
        assertEquals(
            "https://example.com/api/v1/opds",
            buildServerUrl("https://example.com", "/api/v1/opds"),
        )
    }

    @Test
    fun `buildServerUrl trims trailing slash from base`() {
        assertEquals(
            "https://example.com/api/v1/opds",
            buildServerUrl("https://example.com/", "/api/v1/opds"),
        )
    }

    @Test
    fun `buildServerUrl trims multiple trailing slashes`() {
        assertEquals(
            "https://example.com/api",
            buildServerUrl("https://example.com///", "/api"),
        )
    }

    @Test
    fun `buildServerUrl with empty path`() {
        assertEquals(
            "https://example.com",
            buildServerUrl("https://example.com/", ""),
        )
    }

    @Test
    fun `buildServerUrl with empty base`() {
        assertEquals(
            "/api/v1/opds",
            buildServerUrl("", "/api/v1/opds"),
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
    fun `resolveUrl resolves relative href against baseUrl`() {
        assertEquals(
            "https://example.com/api/books",
            resolveUrl("https://example.com", "/api/books"),
        )
    }

    @Test
    fun `resolveUrl resolves relative href trimming trailing slash`() {
        assertEquals(
            "https://example.com/api/books",
            resolveUrl("https://example.com/", "/api/books"),
        )
    }
}
