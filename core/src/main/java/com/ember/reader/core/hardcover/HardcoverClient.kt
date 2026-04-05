package com.ember.reader.core.hardcover

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Singleton
class HardcoverClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: HardcoverTokenManager,
) {

    suspend fun fetchMe(): Result<HardcoverUser> = runCatching {
        val json = query(
            """
            query {
                me {
                    id
                    username
                    name
                    books_count
                }
            }
            """.trimIndent()
        )
        val me = json.obj("data").obj("me")
        HardcoverUser(
            id = me.int("id"),
            username = me.str("username"),
            name = me.strOrNull("name"),
            booksCount = me.intOrDefault("books_count", 0),
        )
    }

    suspend fun fetchBooksByStatus(userId: Int, statusId: Int): Result<List<HardcoverBook>> = runCatching {
        val json = query(
            """
            query {
                user_books(
                    where: {user_id: {_eq: $userId}, status_id: {_eq: $statusId}}
                    order_by: {updated_at: desc}
                    limit: 50
                ) {
                    id
                    status_id
                    rating
                    date_added
                    book {
                        title
                        pages
                        image { url }
                        contributions(order_by: {author: {name: asc}}, limit: 1) {
                            author { name }
                        }
                    }
                }
            }
            """.trimIndent()
        )
        val userBooks = json.obj("data").arr("user_books")
        userBooks.map { entry ->
            val obj = entry.jsonObject
            val book = obj.obj("book")
            val author = book.arr("contributions")
                .firstOrNull()?.jsonObject
                ?.obj("author")?.strOrNull("name")
            val coverUrl = book.objOrNull("image")?.strOrNull("url")
            HardcoverBook(
                id = obj.int("id"),
                title = book.str("title"),
                author = author,
                coverUrl = coverUrl,
                statusId = obj.int("status_id"),
                rating = obj.floatOrNull("rating"),
                dateAdded = obj.strOrNull("date_added"),
                pages = book.intOrNull("pages"),
            )
        }
    }

    private suspend fun query(graphql: String): JsonObject {
        val token = tokenManager.getToken()
            ?: error("Not connected to Hardcover")

        val response = httpClient.post(API_URL) {
            contentType(ContentType.Application.Json)
            header("authorization", token)
            setBody(GraphqlRequest(query = graphql))
        }
        if (!response.status.isSuccess()) {
            error("Hardcover API error: ${response.status}")
        }
        val body = response.body<JsonObject>()
        val errors = body["errors"]
        if (errors is JsonArray && errors.isNotEmpty()) {
            val msg = errors.first().jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown error"
            error("Hardcover: $msg")
        }
        return body
    }

    companion object {
        private const val API_URL = "https://api.hardcover.app/v1/graphql"
    }
}

@Serializable
private data class GraphqlRequest(
    val query: String,
)

// JSON helpers
private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
private fun JsonObject.objOrNull(key: String): JsonObject? = get(key)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
private fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray
private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.strOrNull(key: String): String? = get(key)?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.content
private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
private fun JsonObject.intOrNull(key: String): Int? = get(key)?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.intOrNull
private fun JsonObject.intOrDefault(key: String, default: Int): Int = intOrNull(key) ?: default
private fun JsonObject.floatOrNull(key: String): Float? = get(key)?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.floatOrNull
