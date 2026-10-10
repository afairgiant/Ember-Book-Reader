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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MetadataClientTest {

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

    private fun createClient(engine: MockEngine): MetadataClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        return MetadataClient(httpClient, GrimmoryTokenManager(credentialEncryption, httpClient))
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `getEnabledSearchProviders returns enabled providers in UI order`() = runTest {
        var capturedUrl: String? = null
        var capturedAuth: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedAuth = request.headers["Authorization"]
            respond(
                """
                [{"name":"GoodReads","enabled":true},{"name":"Comicvine","enabled":false},
                 {"name":"Hardcover","enabled":true},{"name":"Google","enabled":false},
                 {"name":"Amazon","enabled":true},{"name":"AppleBooks","enabled":false},
                 {"name":"Audible","enabled":true},{"name":"OpenLibrary","enabled":false}]
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders()
            )
        }

        val providers = createClient(engine).getEnabledSearchProviders(baseUrl, serverId).getOrThrow()

        assertEquals(
            listOf(
                MetadataProvider.Amazon,
                MetadataProvider.GoodReads,
                MetadataProvider.Hardcover,
                MetadataProvider.Audible
            ),
            providers
        )
        assertEquals("http://grimmory.test/api/v1/metadata/providers", capturedUrl)
        assertEquals("Bearer test-token", capturedAuth)
    }

    @Test
    fun `getEnabledSearchProviders ignores providers this build does not know`() = runTest {
        val engine = MockEngine {
            respond(
                """[{"name":"SomeFutureProvider","enabled":true},{"name":"OpenLibrary","enabled":true}]""",
                HttpStatusCode.OK,
                jsonHeaders()
            )
        }

        val providers = createClient(engine).getEnabledSearchProviders(baseUrl, serverId).getOrThrow()

        assertEquals(listOf(MetadataProvider.OpenLibrary), providers)
    }

    @Test
    fun `getEnabledSearchProviders leaves out enabled providers Ember does not offer`() = runTest {
        val engine = MockEngine {
            respond(
                """[{"name":"Comicvine","enabled":true},{"name":"Douban","enabled":true}]""",
                HttpStatusCode.OK,
                jsonHeaders()
            )
        }

        val providers = createClient(engine).getEnabledSearchProviders(baseUrl, serverId).getOrThrow()

        assertTrue(providers.isEmpty())
    }

    @Test
    fun `getEnabledSearchProviders fails with 404 on servers without the endpoint`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.NotFound, jsonHeaders()) }

        val result = createClient(engine).getEnabledSearchProviders(baseUrl, serverId)

        assertEquals(404, (result.exceptionOrNull() as GrimmoryHttpException).statusCode)
    }
}
