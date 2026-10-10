package com.ember.reader.core.opds

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpdsClientTest {

    private val baseUrl = "http://grimmory.test/api/v1/opds"

    // basicAuthHeader uses android.util.Base64, which is not available in JVM unit tests.
    @BeforeEach
    fun setUp() {
        mockkStatic(BASIC_AUTH_FILE)
        every { com.ember.reader.core.network.basicAuthHeader(any(), any()) } returns "Basic dGVzdA=="
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(BASIC_AUTH_FILE)
    }

    private fun navigationFeed(
        entryTitles: List<String>,
        nextHref: String?,
        nextKind: String = "navigation"
    ): String {
        val next = nextHref?.let {
            """<link rel="next" href="$it" type="application/atom+xml;profile=opds-catalog;kind=$nextKind"/>"""
        }.orEmpty()
        val entries = entryTitles.joinToString("\n") { title ->
            """
            <entry>
              <id>urn:author:$title</id>
              <title>$title</title>
              <link rel="subsection" href="/api/v1/opds/catalog?author=$title" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
            </entry>
            """.trimIndent()
        }
        // No XML declaration: the interpolated entries defeat trimIndent, and a declaration
        // preceded by whitespace is not well-formed.
        return """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Authors</title>
              $next
              $entries
            </feed>
        """.trimIndent()
    }

    private fun clientFor(requestedUrls: MutableList<String>, pages: Map<String, String>): OpdsClient {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requestedUrls += url
            val body = pages[url]
            if (body == null) {
                respond("", HttpStatusCode.NotFound)
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/atom+xml"))
            }
        }
        return OpdsClient(HttpClient(engine) { expectSuccess = false })
    }

    @Test
    fun `fetchCatalog follows next links and merges navigation pages`() = runTest {
        val requested = mutableListOf<String>()
        val client = clientFor(
            requested,
            mapOf(
                "http://grimmory.test/api/v1/opds/authors" to
                    navigationFeed(listOf("A", "B"), "/api/v1/opds/authors?page=2&amp;size=50"),
                "http://grimmory.test/api/v1/opds/authors?page=2&size=50" to
                    navigationFeed(listOf("C"), null)
            )
        )

        val feed = client.fetchCatalog(baseUrl, "u", "p", "/api/v1/opds/authors").getOrThrow()

        assertEquals("Authors", feed.title)
        assertEquals(listOf("A", "B", "C"), feed.entries.map { it.title })
        assertEquals(2, requested.size)
    }

    @Test
    fun `fetchCatalog makes a single request when feed has no next link`() = runTest {
        val requested = mutableListOf<String>()
        val client = clientFor(
            requested,
            mapOf("http://grimmory.test/api/v1/opds" to navigationFeed(listOf("A"), null))
        )

        val feed = client.fetchCatalog(baseUrl, "u", "p").getOrThrow()

        assertEquals(listOf("A"), feed.entries.map { it.title })
        assertEquals(1, requested.size)
    }

    @Test
    fun `fetchCatalog does not follow next link of acquisition feed`() = runTest {
        val requested = mutableListOf<String>()
        val client = clientFor(
            requested,
            mapOf(
                "http://grimmory.test/api/v1/opds/catalog" to
                    navigationFeed(listOf("A"), "/api/v1/opds/catalog?page=2", nextKind = "acquisition")
            )
        )

        client.fetchCatalog(baseUrl, "u", "p", "/api/v1/opds/catalog").getOrThrow()

        assertEquals(1, requested.size)
    }

    @Test
    fun `fetchCatalog fails when a later page fails`() = runTest {
        val requested = mutableListOf<String>()
        val client = clientFor(
            requested,
            mapOf(
                "http://grimmory.test/api/v1/opds/authors" to
                    navigationFeed(listOf("A"), "/api/v1/opds/authors?page=2&amp;size=50")
            )
        )

        assertTrue(client.fetchCatalog(baseUrl, "u", "p", "/api/v1/opds/authors").isFailure)
    }

    private companion object {
        const val BASIC_AUTH_FILE = "com.ember.reader.core.network.BasicAuthKt"
    }
}
