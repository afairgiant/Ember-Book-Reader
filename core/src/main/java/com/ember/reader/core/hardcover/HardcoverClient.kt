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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class HardcoverClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: HardcoverTokenManager
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
        val meData = json.obj("data")["me"]!!
        // me can be a single object or an array — handle both
        val me = if (meData is JsonArray) meData.first().jsonObject else meData.jsonObject
        HardcoverUser(
            id = me.int("id"),
            username = me.str("username"),
            name = me.strOrNull("name"),
            booksCount = me.intOrDefault("books_count", 0)
        )
    }

    suspend fun fetchBooksByStatus(userId: Int, statusId: Int): Result<List<HardcoverBook>> =
        runCatching {
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
                    book_id
                    book {
                        title
                        slug
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
                    bookId = obj.int("book_id"),
                    title = book.str("title"),
                    author = author,
                    coverUrl = coverUrl,
                    statusId = obj.int("status_id"),
                    rating = obj.floatOrNull("rating"),
                    dateAdded = obj.strOrNull("date_added"),
                    pages = book.intOrNull("pages"),
                    slug = book.strOrNull("slug")
                )
            }
        }

    suspend fun searchBooks(
        searchQuery: String,
        limit: Int = 3
    ): Result<List<HardcoverSearchResult>> = runCatching {
        val escaped = searchQuery.replace("\"", "\\\"")
        val json = query(
            """
            query {
                search(query: "$escaped", query_type: "Book", per_page: $limit) {
                    results
                }
            }
            """.trimIndent()
        )
        val searchData = json.obj("data").obj("search")
        val results = searchData["results"]
        if (results == null || results is kotlinx.serialization.json.JsonNull) return@runCatching emptyList()

        results.jsonArray
            .filter { it.jsonObject.containsKey("document") }
            .map { it.jsonObject.obj("document") }
            .map { doc ->
                HardcoverSearchResult(
                    bookId = doc.int("id"),
                    title = doc.str("title"),
                    slug = doc.str("slug"),
                    averageRating = doc.floatOrNull("rating"),
                    ratingsCount = doc.intOrDefault("ratings_count", 0),
                    authors = doc["author_names"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                )
            }
    }

    suspend fun fetchUserBookEntry(userId: Int, bookId: Int): Result<HardcoverUserBookEntry?> =
        runCatching {
            val json = query(
                """
            query {
                user_books(
                    where: {user_id: {_eq: $userId}, book_id: {_eq: $bookId}}
                    limit: 1
                ) {
                    status_id
                    rating
                    date_added
                }
            }
                """.trimIndent()
            )
            val entries = json.obj("data").arr("user_books")
            if (entries.isEmpty()) return@runCatching null
            val entry = entries.first().jsonObject
            HardcoverUserBookEntry(
                statusId = entry.int("status_id"),
                rating = entry.floatOrNull("rating"),
                dateAdded = entry.strOrNull("date_added")
            )
        }

    suspend fun fetchBookDetail(bookId: Int): Result<HardcoverBookDetail> = runCatching {
        val json = query(
            """
            query {
                books(where: {id: {_eq: $bookId}}, limit: 1) {
                    id
                    title
                    subtitle
                    description
                    slug
                    pages
                    release_year
                    rating
                    ratings_count
                    image { url }
                    contributions(order_by: {author: {name: asc}}) {
                        author { name }
                    }
                    book_series {
                        series { name }
                        position
                    }
                }
            }
            """.trimIndent()
        )
        val books = json.obj("data").arr("books")
        if (books.isEmpty()) error("Book not found")
        val book = books.first().jsonObject
        val authors = book.arr("contributions").map {
            it.jsonObject.obj("author").str("name")
        }
        val seriesEntry = book.arr("book_series").firstOrNull()?.jsonObject
        HardcoverBookDetail(
            id = book.int("id"),
            title = book.str("title"),
            subtitle = book.strOrNull("subtitle"),
            description = book.strOrNull("description"),
            slug = book.str("slug"),
            pages = book.intOrNull("pages"),
            releaseYear = book.intOrNull("release_year"),
            averageRating = book.floatOrNull("rating"),
            ratingsCount = book.intOrDefault("ratings_count", 0),
            coverUrl = book.objOrNull("image")?.strOrNull("url"),
            authors = authors,
            seriesName = seriesEntry?.objOrNull("series")?.strOrNull("name"),
            seriesPosition = seriesEntry?.floatOrNull("position")
        )
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
    val query: String
)

// JSON helpers
private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
private fun JsonObject.objOrNull(key: String): JsonObject? = get(key)?.takeIf {
    it !is kotlinx.serialization.json.JsonNull
}?.jsonObject
private fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray
private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.strOrNull(key: String): String? = get(key)?.takeIf {
    it is JsonPrimitive
}?.jsonPrimitive?.content
private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
private fun JsonObject.intOrNull(key: String): Int? = get(key)?.takeIf {
    it is JsonPrimitive
}?.jsonPrimitive?.intOrNull
private fun JsonObject.intOrDefault(key: String, default: Int): Int = intOrNull(key) ?: default
private fun JsonObject.floatOrNull(key: String): Float? = get(key)?.takeIf {
    it is JsonPrimitive
}?.jsonPrimitive?.floatOrNull
