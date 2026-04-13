package com.ember.reader.core.grimmory

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class GrimmoryAppClientOrganizeTest {

    @MockK
    private lateinit var tokenManager: GrimmoryTokenManager

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = "http://grimmory.test"
    private val serverId = 1L

    private fun client(
        responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): Pair<GrimmoryAppClient, MutableList<HttpRequestData>> {
        val captured = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            captured += request
            responder(request)
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
        coEvery { tokenManager.withAuth<Any>(baseUrl, serverId, any()) } coAnswers {
            val block = thirdArg<suspend (String) -> Any>()
            runCatching { block("test-token") }
        }
        return GrimmoryAppClient(http, tokenManager) to captured
    }

    @Test
    fun `getFullLibraries parses paths and fileNamingPattern`() = runTest {
        val body = """
            [
              {
                "id": 1,
                "name": "Fiction",
                "fileNamingPattern": "{authors:sort}/{title} ({year})",
                "paths": [ { "id": 10, "libraryId": 1, "path": "/mnt/books/fiction" } ]
              },
              {
                "id": 2,
                "name": "Non-fiction",
                "paths": []
              }
            ]
        """.trimIndent()

        val (client, captured) = client {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val result = client.getFullLibraries(baseUrl, serverId)
        assertTrue(result.isSuccess)
        val libs = result.getOrNull()!!
        assertEquals(2, libs.size)
        assertEquals("Fiction", libs[0].name)
        assertEquals("{authors:sort}/{title} ({year})", libs[0].fileNamingPattern)
        assertEquals(1, libs[0].paths.size)
        assertEquals(10L, libs[0].paths[0].id)
        assertEquals("/mnt/books/fiction", libs[0].paths[0].path)
        assertEquals(0, libs[1].paths.size)
        assertEquals("$baseUrl/api/v1/libraries", captured[0].url.toString())
        assertEquals("Bearer test-token", captured[0].headers["Authorization"])
    }

    @Test
    fun `moveFiles sends the exact JSON shape Grimmory expects`() = runTest {
        val (client, captured) = client {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
        }

        val request = FileMoveRequest(
            bookIds = setOf(101L, 102L),
            moves = listOf(
                FileMoveItem(bookId = 101L, targetLibraryId = 2L, targetLibraryPathId = 20L),
                FileMoveItem(bookId = 102L, targetLibraryId = 2L, targetLibraryPathId = 20L)
            )
        )
        val result = client.moveFiles(baseUrl, serverId, request)
        assertTrue(result.isSuccess)

        val call = captured.single()
        assertEquals("$baseUrl/api/v1/files/move", call.url.toString())
        assertEquals("POST", call.method.value)

        val bodyText = (call.body as TextContent).text
        val parsed = json.decodeFromString(JsonObject.serializer(), bodyText)
        assertEquals(setOf("bookIds", "moves"), parsed.keys)
        val moves = parsed["moves"]!!.jsonArray
        assertEquals(2, moves.size)
        val first = moves[0].jsonObject
        assertEquals(setOf("bookId", "targetLibraryId", "targetLibraryPathId"), first.keys)
        assertEquals(101L, first["bookId"]!!.jsonPrimitive.long)
        assertEquals(2L, first["targetLibraryId"]!!.jsonPrimitive.long)
        assertEquals(20L, first["targetLibraryPathId"]!!.jsonPrimitive.long)
    }

    @Test
    fun `moveFiles surfaces 403 as Result failure`() = runTest {
        val (client, _) = client {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Forbidden)
        }
        val request = FileMoveRequest(setOf(1L), listOf(FileMoveItem(1L, 1L, 1L)))
        val result = client.moveFiles(baseUrl, serverId, request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
    }

    @Test
    fun `getCurrentUser parses permissions including canMoveOrganizeFiles`() = runTest {
        val body = """
            {
              "id": 42,
              "username": "alex",
              "permissions": {
                "isAdmin": false,
                "canManageLibrary": true,
                "canMoveOrganizeFiles": true
              }
            }
        """.trimIndent()

        val (client, captured) = client {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val result = client.getCurrentUser(baseUrl, serverId)
        assertTrue(result.isSuccess)
        val user = result.getOrNull()!!
        assertEquals("alex", user.username)
        assertTrue(user.permissions.canMoveOrganizeFiles)
        assertTrue(user.permissions.canManageLibrary)
        assertFalse(user.permissions.isAdmin)
        assertEquals("$baseUrl/api/v1/users/me", captured[0].url.toString())
    }

    @Test
    fun `getCurrentUser returns permissions all false when fields are missing`() = runTest {
        val body = """{"id": 42, "username": "alex"}"""
        val (client, _) = client {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val user = client.getCurrentUser(baseUrl, serverId).getOrNull()!!
        assertFalse(user.permissions.isAdmin)
        assertFalse(user.permissions.canManageLibrary)
        assertFalse(user.permissions.canMoveOrganizeFiles)
    }
}
