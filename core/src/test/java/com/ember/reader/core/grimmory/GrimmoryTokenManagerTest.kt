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
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class GrimmoryTokenManagerTest {

    @MockK
    private lateinit var credentialEncryption: CredentialEncryption

    private val baseUrl = "http://grimmory.test"
    private val serverId = 1L
    private val json = Json { ignoreUnknownKeys = true }

    private fun tokenManager(
        engine: MockEngine = MockEngine { error("No HTTP expected") }
    ): GrimmoryTokenManager {
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        return GrimmoryTokenManager(credentialEncryption, http)
    }

    private fun stubTokens(access: String?, refresh: String? = null) {
        every { credentialEncryption.getPassword("grimmory_access_$serverId") } returns access
        every { credentialEncryption.getPassword("grimmory_refresh_$serverId") } returns refresh
    }

    private fun stubStoreTokens() {
        every { credentialEncryption.storePassword(any(), any()) } returns Unit
    }

    private fun refreshEngine(
        newAccess: String = "new-access",
        newRefresh: String = "new-refresh",
        status: HttpStatusCode = HttpStatusCode.OK
    ): MockEngine = MockEngine {
        respond(
            content = ByteReadChannel(
                """{"accessToken":"$newAccess","refreshToken":"$newRefresh"}"""
            ),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @Test
    fun `withAuth passes token to block on success`() = runTest {
        stubTokens(access = "valid-token")
        val manager = tokenManager()

        val result = manager.withAuth(baseUrl, serverId) { token ->
            "got:$token"
        }

        assertTrue(result.isSuccess)
        assertEquals("got:valid-token", result.getOrNull())
    }

    @Test
    fun `withAuth refreshes token and retries on 401`() = runTest {
        stubTokens(access = "expired-token", refresh = "my-refresh")
        stubStoreTokens()
        val engine = refreshEngine()
        val manager = tokenManager(engine)

        val calls = mutableListOf<String>()
        val result = manager.withAuth(baseUrl, serverId) { token ->
            calls += token
            if (token == "expired-token") {
                throw GrimmoryHttpException(401, "Unauthorized")
            }
            "ok:$token"
        }

        assertTrue(result.isSuccess)
        assertEquals("ok:new-access", result.getOrNull())
        assertEquals(listOf("expired-token", "new-access"), calls)
        verify { credentialEncryption.storePassword("grimmory_access_$serverId", "new-access") }
        verify { credentialEncryption.storePassword("grimmory_refresh_$serverId", "new-refresh") }
    }

    @Test
    fun `withAuth fails when refresh endpoint returns error`() = runTest {
        stubTokens(access = "expired-token", refresh = "my-refresh")
        val engine = refreshEngine(status = HttpStatusCode.Forbidden)
        val manager = tokenManager(engine)

        val result = manager.withAuth(baseUrl, serverId) { token ->
            throw GrimmoryHttpException(401, "Unauthorized")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Token refresh failed") == true)
    }

    @Test
    fun `withAuth fails immediately when no access token stored`() = runTest {
        stubTokens(access = null)
        val manager = tokenManager()

        val result = manager.withAuth(baseUrl, serverId) { "should not run" }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not logged in") == true)
    }

    @Test
    fun `withAuth propagates non-401 errors without refresh attempt`() = runTest {
        stubTokens(access = "valid-token")
        val manager = tokenManager()

        val result = manager.withAuth(baseUrl, serverId) { _ ->
            throw GrimmoryHttpException(500, "Internal Server Error")
        }

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is GrimmoryHttpException)
        assertEquals(500, (exception as GrimmoryHttpException).statusCode)
    }
}
