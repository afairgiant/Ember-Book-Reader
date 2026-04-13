package com.ember.reader.core.hardcover

import com.ember.reader.core.network.CredentialEncryption
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class HardcoverClientTest {

    private val credentialEncryption: CredentialEncryption = mockk()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setUp() {
        every { credentialEncryption.getPassword("hardcover_api_token") } returns "Bearer test-hc-token"
    }

    private fun createClient(engine: MockEngine): HardcoverClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        val tokenManager = HardcoverTokenManager(credentialEncryption)
        return HardcoverClient(httpClient, tokenManager)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(content: String) =
        respond(
            content = ByteReadChannel(content),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    @Test
    fun `fetchMe sends GraphQL query with authorization header`() = runTest {
        var capturedAuth: String? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedAuth = request.headers["authorization"]
            capturedBody = (request.body as TextContent).text
            jsonResponse("""{"data":{"me":{"id":42,"username":"alex","name":"Alex","books_count":100}}}""")
        }
        val client = createClient(engine)

        val result = client.fetchMe()

        assertTrue(result.isSuccess)
        val user = result.getOrNull()!!
        assertEquals(42, user.id)
        assertEquals("alex", user.username)
        assertEquals("Alex", user.name)
        assertEquals(100, user.booksCount)
        assertEquals("Bearer test-hc-token", capturedAuth)
        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("me"))
    }

    @Test
    fun `searchBooks deserializes results with document objects`() = runTest {
        val responseJson = """
            {"data":{"search":{"results":[
                {"document":{"id":1,"title":"Dune","slug":"dune","rating":4.5,"ratings_count":10000,"author_names":["Frank Herbert"]}},
                {"document":{"id":2,"title":"Dune Messiah","slug":"dune-messiah","rating":4.0,"ratings_count":5000,"author_names":["Frank Herbert"]}}
            ]}}}
        """.trimIndent()

        val engine = MockEngine { jsonResponse(responseJson) }
        val client = createClient(engine)

        val result = client.searchBooks("Dune")

        assertTrue(result.isSuccess)
        val books = result.getOrNull()!!
        assertEquals(2, books.size)
        assertEquals("Dune", books[0].title)
        assertEquals("dune", books[0].slug)
        assertEquals(listOf("Frank Herbert"), books[0].authors)
    }

    @Test
    fun `searchBooks handles null results`() = runTest {
        val engine = MockEngine {
            jsonResponse("""{"data":{"search":{"results":null}}}""")
        }
        val client = createClient(engine)

        val result = client.searchBooks("nonexistent")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `fetchBookDetail deserializes full book with series and authors`() = runTest {
        val responseJson = """
            {"data":{"books":[{
                "id":123,"title":"The Name of the Wind","subtitle":null,
                "description":"A fantasy novel","slug":"the-name-of-the-wind",
                "pages":662,"release_year":2007,"rating":4.56,"ratings_count":50000,
                "image":{"url":"https://covers.hardcover.app/123.jpg"},
                "contributions":[{"author":{"name":"Patrick Rothfuss"}}],
                "book_series":[{"series":{"name":"The Kingkiller Chronicle"},"position":1.0}]
            }]}}
        """.trimIndent()

        val engine = MockEngine { jsonResponse(responseJson) }
        val client = createClient(engine)

        val result = client.fetchBookDetail(123)

        assertTrue(result.isSuccess)
        val detail = result.getOrNull()!!
        assertEquals(123, detail.id)
        assertEquals("The Name of the Wind", detail.title)
        assertEquals(662, detail.pages)
        assertEquals(2007, detail.releaseYear)
        assertEquals(listOf("Patrick Rothfuss"), detail.authors)
        assertEquals("The Kingkiller Chronicle", detail.seriesName)
        assertEquals(1.0f, detail.seriesPosition)
        assertEquals("https://covers.hardcover.app/123.jpg", detail.coverUrl)
    }

    @Test
    fun `GraphQL errors are propagated as failures`() = runTest {
        val engine = MockEngine {
            jsonResponse("""{"data":null,"errors":[{"message":"Access denied"}]}""")
        }
        val client = createClient(engine)

        val result = client.fetchMe()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Access denied") == true)
    }
}
