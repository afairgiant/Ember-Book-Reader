package com.ember.reader.core.grimmory

import com.ember.reader.core.network.CredentialEncryption
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class GrimmoryClientTest {

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

    private fun createClient(engine: MockEngine): GrimmoryClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        val tokenManager = GrimmoryTokenManager(credentialEncryption, httpClient)
        return GrimmoryClient(httpClient, tokenManager)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) = respond(
        content = ByteReadChannel(content),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    @Nested
    inner class HealthCheck {

        @Test
        fun `checkHealth returns true on 200`() = runTest {
            val engine = MockEngine { jsonResponse("{}") }
            val client = createClient(engine)

            assertTrue(client.checkHealth(baseUrl))
        }

        @Test
        fun `checkHealth returns false on server error`() = runTest {
            val engine = MockEngine { jsonResponse("{}", HttpStatusCode.InternalServerError) }
            val client = createClient(engine)

            assertTrue(!client.checkHealth(baseUrl))
        }
    }

    @Nested
    inner class Auth {

        @Test
        fun `login sends correct JSON body and deserializes tokens`() = runTest {
            var capturedBody: String? = null
            val engine = MockEngine { request ->
                capturedBody = (request.body as TextContent).text
                jsonResponse("""{"accessToken":"at","refreshToken":"rt","isDefaultPassword":null}""")
            }
            val client = createClient(engine)

            val result = client.login(baseUrl, "admin", "pass123")

            assertTrue(result.isSuccess)
            val tokens = result.getOrNull()!!
            assertEquals("at", tokens.accessToken)
            assertEquals("rt", tokens.refreshToken)

            val body = json.decodeFromString<JsonObject>(capturedBody!!)
            assertEquals("admin", body["username"]?.jsonPrimitive?.content)
            assertEquals("pass123", body["password"]?.jsonPrimitive?.content)
        }

        @Test
        fun `login returns failure on 401`() = runTest {
            val engine = MockEngine {
                jsonResponse("""{"error":"Invalid credentials"}""", HttpStatusCode.Unauthorized)
            }
            val client = createClient(engine)

            val result = client.login(baseUrl, "admin", "wrong")

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is GrimmoryHttpException)
            assertEquals(401, (exception as GrimmoryHttpException).statusCode)
        }
    }

    @Nested
    inner class Books {

        @Test
        fun `getBookDetail sends correct URL and includes Bearer token`() = runTest {
            var capturedUrl: String? = null
            var capturedAuth: String? = null
            val responseJson = """
                {"id":101,"title":"My Book","readProgress":0.5,"authors":["Author A"],
                 "primaryFile":{"id":1,"fileName":"book.epub"}}
            """.trimIndent()

            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                capturedAuth = request.headers["Authorization"]
                jsonResponse(responseJson)
            }
            val client = createClient(engine)

            val result = client.getBookDetail(baseUrl, serverId, 101L)

            assertTrue(result.isSuccess)
            val detail = result.getOrNull()!!
            assertEquals(101L, detail.id)
            assertEquals("My Book", detail.title)
            assertEquals(0.5f, detail.readProgress)
            assertEquals(listOf("Author A"), detail.authors)
            assertNotNull(detail.primaryFile)
            assertEquals(1L, detail.primaryFile!!.id)

            assertTrue(capturedUrl!!.contains("/api/v1/app/books/101"))
            assertEquals("Bearer test-token", capturedAuth)
        }

        @Test
        fun `getContinueReading sends limit parameter`() = runTest {
            var capturedUrl: String? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                jsonResponse("""[{"id":1,"title":"Book 1","authors":[]}]""")
            }
            val client = createClient(engine)

            val result = client.getContinueReading(baseUrl, serverId, limit = 5)

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()!!.size)
            assertTrue(capturedUrl!!.contains("limit=5"))
        }
    }

    @Nested
    inner class Progress {

        @Test
        fun `putAppBookProgress serializes request and targets app progress endpoint`() = runTest {
            var capturedBody: String? = null
            var capturedUrl: String? = null
            var capturedMethod: HttpMethod? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                capturedMethod = request.method
                capturedBody = (request.body as TextContent).text
                jsonResponse("{}")
            }
            val client = createClient(engine)

            val request = GrimmoryUpdateProgressRequest(
                fileProgress = GrimmoryFileProgress(bookFileId = 5L, progressPercent = 50.0f),
                epubProgress = GrimmoryEpubProgress(cfi = "epubcfi(/6/2)", percentage = 50.0f)
            )

            val result = client.putAppBookProgress(baseUrl, serverId, 101L, request)

            assertTrue(result.isSuccess)
            assertEquals(HttpMethod.Put, capturedMethod)
            assertTrue(capturedUrl!!.contains("/api/v1/app/books/101/progress"))

            val body = json.decodeFromString<JsonObject>(capturedBody!!)
            // bookId is path-bound in the v3.0.0 endpoint, so it must NOT appear in the body.
            assertTrue(!body.containsKey("bookId"))
            val fileProgress = body["fileProgress"] as JsonObject
            assertEquals("5", fileProgress["bookFileId"]?.jsonPrimitive?.content)
        }

        @Test
        fun `getAppBookProgress deserializes response and targets app progress endpoint`() = runTest {
            var capturedUrl: String? = null
            val responseJson = """
                {"readProgress":0.42,"readStatus":"READING","lastReadTime":"2026-04-22T10:00:00Z",
                 "epubProgress":{"cfi":"epubcfi(/6/4)","href":"ch1.xhtml","percentage":42.0},
                 "koreaderProgress":{"percentage":0.42,"device":"KOReader","deviceId":"uuid-1","lastSyncTime":"2026-04-22T10:00:00Z"}}
            """.trimIndent()

            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                jsonResponse(responseJson)
            }
            val client = createClient(engine)

            val result = client.getAppBookProgress(baseUrl, serverId, 101L)

            assertTrue(result.isSuccess)
            val progress = result.getOrNull()!!
            assertEquals(0.42f, progress.readProgress)
            assertEquals(ReadStatus.READING, progress.readStatus)
            assertEquals(42.0f, progress.epubProgress!!.percentage)
            assertEquals("KOReader", progress.koreaderProgress!!.device)
            assertEquals(0.42f, progress.koreaderProgress!!.percentage)
            assertTrue(capturedUrl!!.contains("/api/v1/app/books/101/progress"))
        }
    }

    @Nested
    inner class Annotations {

        @Test
        fun `getAnnotations deserializes list`() = runTest {
            val responseJson = """
                [{"id":1,"cfi":"/6/4","text":"hello","color":"FACC15","note":"a note","chapterTitle":"Ch 1","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"},
                 {"id":2,"cfi":"/6/8","text":"world","color":"4ADE80"}]
            """.trimIndent()

            val engine = MockEngine { jsonResponse(responseJson) }
            val client = createClient(engine)

            val result = client.getAnnotations(baseUrl, serverId, 101L)

            assertTrue(result.isSuccess)
            val annotations = result.getOrNull()!!
            assertEquals(2, annotations.size)
            assertEquals("/6/4", annotations[0].cfi)
            assertEquals("hello", annotations[0].text)
            assertEquals("FACC15", annotations[0].color)
            assertEquals("a note", annotations[0].note)
        }

        @Test
        fun `createAnnotation sends correct request and returns created entity`() = runTest {
            var capturedBody: String? = null
            val engine = MockEngine { request ->
                capturedBody = (request.body as TextContent).text
                jsonResponse("""{"id":99,"cfi":"/6/4","text":"hello","color":"FACC15"}""")
            }
            val client = createClient(engine)

            val request = CreateAnnotationRequest(
                bookId = 101L, cfi = "/6/4", text = "hello",
                color = "#FFEB3B", note = "test note"
            )
            val result = client.createAnnotation(baseUrl, serverId, request)

            assertTrue(result.isSuccess)
            assertEquals(99L, result.getOrNull()!!.id)

            val body = json.decodeFromString<JsonObject>(capturedBody!!)
            assertEquals("/6/4", body["cfi"]?.jsonPrimitive?.content)
            assertEquals("hello", body["text"]?.jsonPrimitive?.content)
        }

        @Test
        fun `deleteAnnotation sends DELETE to correct URL`() = runTest {
            var capturedUrl: String? = null
            var capturedMethod: HttpMethod? = null
            val engine = MockEngine { request ->
                capturedUrl = request.url.toString()
                capturedMethod = request.method
                jsonResponse("{}")
            }
            val client = createClient(engine)

            val result = client.deleteAnnotation(baseUrl, serverId, 42L)

            assertTrue(result.isSuccess)
            assertTrue(capturedUrl!!.contains("/api/v1/annotations/42"))
            assertEquals(HttpMethod.Delete, capturedMethod)
        }
    }

    @Nested
    inner class Bookmarks {

        @Test
        fun `getBookmarks deserializes list`() = runTest {
            val responseJson = """
                [{"id":1,"cfi":"/6/4","title":"Ch 1"},
                 {"id":2,"cfi":"/6/8","title":"Ch 2"}]
            """.trimIndent()

            val engine = MockEngine { jsonResponse(responseJson) }
            val client = createClient(engine)

            val result = client.getBookmarks(baseUrl, serverId, 101L)

            assertTrue(result.isSuccess)
            val bookmarks = result.getOrNull()!!
            assertEquals(2, bookmarks.size)
            assertEquals("/6/4", bookmarks[0].cfi)
            assertEquals("Ch 1", bookmarks[0].title)
        }

        @Test
        fun `createBookmark sends correct request`() = runTest {
            var capturedBody: String? = null
            val engine = MockEngine { request ->
                capturedBody = (request.body as TextContent).text
                jsonResponse("""{"id":50,"cfi":"/6/4","title":"Ch 1"}""")
            }
            val client = createClient(engine)

            val request = CreateBookmarkRequest(bookId = 101L, cfi = "/6/4", title = "Ch 1")
            val result = client.createBookmark(baseUrl, serverId, request)

            assertTrue(result.isSuccess)
            assertEquals(50L, result.getOrNull()!!.id)

            val body = json.decodeFromString<JsonObject>(capturedBody!!)
            assertEquals("101", body["bookId"]?.jsonPrimitive?.content)
            assertEquals("/6/4", body["cfi"]?.jsonPrimitive?.content)
        }
    }

    @Nested
    inner class Stats {

        @Test
        fun `getReadingStreak deserializes response`() = runTest {
            val responseJson = """
                {"currentStreak":5,"longestStreak":10,"totalReadingDays":42,"last52Weeks":[{"date":"2026-01-01","active":true}]}
            """.trimIndent()

            val engine = MockEngine { jsonResponse(responseJson) }
            val client = createClient(engine)

            val result = client.getReadingStreak(baseUrl, serverId)

            assertTrue(result.isSuccess)
            val streak = result.getOrNull()!!
            assertEquals(5, streak.currentStreak)
            assertEquals(10, streak.longestStreak)
            assertEquals(42, streak.totalReadingDays)
            assertEquals(1, streak.last52Weeks.size)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `non-2xx response wraps in GrimmoryHttpException`() = runTest {
            val engine = MockEngine {
                jsonResponse("""{"error":"forbidden"}""", HttpStatusCode.Forbidden)
            }
            val client = createClient(engine)

            val result = client.getBookDetail(baseUrl, serverId, 101L)

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is GrimmoryHttpException)
            assertEquals(403, (exception as GrimmoryHttpException).statusCode)
        }
    }

    @Nested
    inner class UrlBuilding {

        @Test
        fun `audiobookCoverUrl builds correct URL`() {
            val engine = MockEngine { error("unused") }
            val client = createClient(engine)

            val url = client.audiobookCoverUrl(baseUrl, 42L)
            assertEquals("http://grimmory.test/api/v1/audiobooks/42/cover", url)
        }

        @Test
        fun `audiobookStreamUrl includes token as query param`() = runTest {
            val engine = MockEngine { error("unused") }
            val client = createClient(engine)

            val url = client.audiobookStreamUrl(baseUrl, serverId, 42L)

            assertNotNull(url)
            assertTrue(url!!.contains("/api/v1/audiobooks/42/stream"))
            assertTrue(url.contains("token=test-token"))
        }

        @Test
        fun `audiobookStreamUrl with trackIndex builds track URL`() = runTest {
            val engine = MockEngine { error("unused") }
            val client = createClient(engine)

            val url = client.audiobookStreamUrl(baseUrl, serverId, 42L, trackIndex = 3)

            assertNotNull(url)
            assertTrue(url!!.contains("/api/v1/audiobooks/42/track/3/stream"))
        }
    }
}
