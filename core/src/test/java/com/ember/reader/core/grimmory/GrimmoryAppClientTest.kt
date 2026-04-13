package com.ember.reader.core.grimmory

import com.ember.reader.core.network.CredentialEncryption
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class GrimmoryAppClientTest {

    private val credentialEncryption: CredentialEncryption = mockk()
    private val baseUrl = "http://grimmory.test"
    private val serverId = 1L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @BeforeEach
    fun setUp() {
        every { credentialEncryption.getPassword("grimmory_access_$serverId") } returns "test-token"
        every { credentialEncryption.getPassword("grimmory_refresh_$serverId") } returns "test-refresh"
    }

    private fun createClient(engine: MockEngine): GrimmoryAppClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        val tokenManager = GrimmoryTokenManager(credentialEncryption, httpClient)
        return GrimmoryAppClient(httpClient, tokenManager)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = ByteReadChannel(content),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val emptyPageJson = """{"content":[],"totalElements":0,"totalPages":0,"number":0}"""

    @Nested
    inner class GetBooks {

        @Test
        fun `sends all non-null query parameters`() = runTest {
            var capturedUrl: String? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                jsonResponse(emptyPageJson)
            }
            val client = createClient(engine)

            client.getBooks(
                baseUrl, serverId,
                page = 2, size = 25, sort = "title", dir = "asc",
                libraryId = 5L, shelfId = 3L, status = "READING",
                search = "test", fileType = "EPUB",
                minRating = 3, maxRating = 5,
                authors = "Author A", language = "en",
            )

            val url = capturedUrl!!
            assertTrue(url.contains("page=2"))
            assertTrue(url.contains("size=25"))
            assertTrue(url.contains("sort=title"))
            assertTrue(url.contains("dir=asc"))
            assertTrue(url.contains("libraryId=5"))
            assertTrue(url.contains("shelfId=3"))
            assertTrue(url.contains("status=READING"))
            assertTrue(url.contains("search=test"))
            assertTrue(url.contains("fileType=EPUB"))
            assertTrue(url.contains("minRating=3"))
            assertTrue(url.contains("maxRating=5"))
            assertTrue(url.contains("authors=Author"))
            assertTrue(url.contains("language=en"))
        }

        @Test
        fun `omits null optional parameters`() = runTest {
            var capturedUrl: String? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                jsonResponse(emptyPageJson)
            }
            val client = createClient(engine)

            client.getBooks(baseUrl, serverId)

            val url = capturedUrl!!
            // Required defaults present
            assertTrue(url.contains("page=0"))
            assertTrue(url.contains("size=50"))
            // Optional params absent
            assertTrue(!url.contains("libraryId"))
            assertTrue(!url.contains("shelfId"))
            assertTrue(!url.contains("status="))
            assertTrue(!url.contains("search="))
            assertTrue(!url.contains("fileType"))
            assertTrue(!url.contains("minRating"))
        }
    }

    @Nested
    inner class Search {

        @Test
        fun `searchBooks sends q parameter`() = runTest {
            var capturedUrl: String? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                jsonResponse(emptyPageJson)
            }
            val client = createClient(engine)

            client.searchBooks(baseUrl, serverId, query = "Dune")

            val url = capturedUrl!!
            assertTrue(url.contains("/app/books/search"))
            assertTrue(url.contains("q=Dune"))
        }
    }

    @Nested
    inner class CoverUrls {

        @Test
        fun `coverUrl includes version when coverUpdatedOn provided`() {
            val engine = MockEngine { error("unused") }
            val client = createClient(engine)

            val url = client.coverUrl(baseUrl, 42L, coverUpdatedOn = "2026-01-01T12:00:00Z")

            assertTrue(url.contains("/api/v1/media/book/42/cover"))
            assertTrue(url.contains("?v="))
        }

        @Test
        fun `coverUrl omits version when coverUpdatedOn is null`() {
            val engine = MockEngine { error("unused") }
            val client = createClient(engine)

            val url = client.coverUrl(baseUrl, 42L, coverUpdatedOn = null)

            assertTrue(url.contains("/api/v1/media/book/42/cover"))
            assertTrue(!url.contains("?v="))
        }

        @Test
        fun `audiobookCoverUrl uses audiobook endpoint`() {
            val engine = MockEngine { error("unused") }
            val client = createClient(engine)

            val url = client.audiobookCoverUrl(baseUrl, 42L)

            assertEquals("http://grimmory.test/api/v1/audiobooks/42/cover", url)
        }
    }

    @Nested
    inner class SeriesBooks {

        @Test
        fun `getSeriesBooks URL-encodes series name with percent-20`() = runTest {
            var capturedUrl: String? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.encodedPath
                jsonResponse(emptyPageJson)
            }
            val client = createClient(engine)

            client.getSeriesBooks(baseUrl, serverId, "The Lord of the Rings")

            // Should use %20 not + for spaces in path segments
            assertTrue(capturedUrl!!.contains("The%20Lord%20of%20the%20Rings"))
        }
    }
}
