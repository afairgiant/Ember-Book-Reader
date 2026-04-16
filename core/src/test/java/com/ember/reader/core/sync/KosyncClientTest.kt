package com.ember.reader.core.sync

import com.ember.reader.core.network.md5Hash
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KosyncClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val baseUrl = "http://localhost:8080"
    private val username = "testuser"
    private val password = "testpass"

    private fun createClient(engine: MockEngine): KosyncClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        return KosyncClient(httpClient)
    }

    @Test
    fun `authenticate sends correct headers`() = runTest {
        var capturedHeaders: Map<String, String> = emptyMap()

        val engine = MockEngine { request ->
            capturedHeaders = mapOf(
                "x-auth-user" to (request.headers["x-auth-user"] ?: ""),
                "x-auth-key" to (request.headers["x-auth-key"] ?: ""),
                "Accept" to (request.headers["Accept"] ?: "")
            )
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK
            )
        }

        val client = createClient(engine)
        client.authenticate(baseUrl, username, password)

        assertEquals(username, capturedHeaders["x-auth-user"])
        assertEquals(md5Hash(password), capturedHeaders["x-auth-key"])
        assertEquals("application/vnd.koreader.v1+json", capturedHeaders["Accept"])
    }

    @Test
    fun `authenticate returns success on 200`() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
        }

        val client = createClient(engine)
        val result = client.authenticate(baseUrl, username, password)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `authenticate returns failure on 401`() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Unauthorized)
        }

        val client = createClient(engine)
        val result = client.authenticate(baseUrl, username, password)

        assertTrue(result.isFailure)
    }

    @Test
    fun `pushProgress sends correct JSON body`() = runTest {
        var capturedBody: String? = null

        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
        }

        val request = KosyncProgressRequest(
            document = "abc123hash",
            positionData = "{\"locator\":\"data\"}",
            percentage = 0.42f,
            device = "Ember",
            deviceId = "device-uuid-123"
        )

        val client = createClient(engine)
        client.pushProgress(baseUrl, username, password, request)

        assertNotNull(capturedBody)
        val parsed = json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("abc123hash", parsed["document"]?.jsonPrimitive?.content)
        assertEquals("{\"locator\":\"data\"}", parsed["progress"]?.jsonPrimitive?.content)
        assertEquals(0.42f, parsed["percentage"]?.jsonPrimitive?.float)
        assertEquals("Ember", parsed["device"]?.jsonPrimitive?.content)
        assertEquals("device-uuid-123", parsed["device_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `pullProgress deserializes response correctly`() = runTest {
        val responseJson = """
            {
                "document": "abc123hash",
                "progress": "{\"locator\":\"data\"}",
                "percentage": 0.75,
                "device": "KOReader",
                "device_id": "remote-device-id",
                "timestamp": 1700000000
            }
        """.trimIndent()

        val engine = MockEngine {
            respond(
                content = ByteReadChannel(responseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(engine)
        val result = client.pullProgress(baseUrl, username, password, "abc123hash")

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals("abc123hash", response!!.document)
        assertEquals("{\"locator\":\"data\"}", response.positionData)
        assertEquals(0.75f, response.percentage)
        assertEquals("KOReader", response.device)
        assertEquals("remote-device-id", response.deviceId)
        assertEquals(1700000000L, response.timestamp)
    }

    @Test
    fun `pullProgress surfaces 404 as typed KosyncHttpException so callers can refresh`() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
        }

        val client = createClient(engine)
        val result = client.pullProgress(baseUrl, username, password, "abc123hash")

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is KosyncHttpException, "expected KosyncHttpException, got $err")
        assertEquals(404, (err as KosyncHttpException).statusCode)
    }

    @Test
    fun `pullProgress returns null on other non-success statuses`() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
        }

        val client = createClient(engine)
        val result = client.pullProgress(baseUrl, username, password, "abc123hash")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
