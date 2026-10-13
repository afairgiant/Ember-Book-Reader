package com.ember.reader.core.repository

import com.ember.reader.core.coroutine.runCatchingCancellable
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryAppPage
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryNotebookBook
import com.ember.reader.core.grimmory.GrimmoryNotebookEntry
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/** One book's entries in a Grimmory notebook, with the id Ember knows the book by. */
data class GrimmoryNotebook(
    val localBookId: String,
    val entries: List<GrimmoryNotebookEntry>
)

/**
 * What the signed-in Grimmory servers hold.
 *
 * @param failedServers the names of servers that could not be read; their books are missing.
 */
data class GrimmoryNotebooks(
    val notebooks: List<GrimmoryNotebook>,
    val failedServers: List<String>
)

/**
 * Reads the bookmarks, highlights and notes Grimmory keeps (its "notebook"), for showing
 * next to the ones on the device. Nothing read here is stored, except that a book Ember
 * has not seen before is added to its library, not downloaded, so it has a page to open.
 */
@Singleton
class GrimmoryNotebookRepository @Inject constructor(
    private val serverRepository: ServerRepository,
    private val bookRepository: BookRepository,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager
) {

    /** Whether any server could be asked: a Grimmory server that is signed in. */
    suspend fun isAvailable(): Boolean = signedInServers().isNotEmpty()

    suspend fun fetch(): GrimmoryNotebooks {
        val notebooks = mutableListOf<GrimmoryNotebook>()
        val failed = mutableListOf<String>()
        for (server in signedInServers()) {
            runCatchingCancellable { fetchFrom(server) }
                .onSuccess { notebooks += it }
                .onFailure {
                    Timber.w(it, "Notebook: could not read server=${server.id}")
                    failed += server.name
                }
        }
        return GrimmoryNotebooks(notebooks, failed)
    }

    private suspend fun signedInServers(): List<Server> =
        serverRepository.getAll().filter { it.isGrimmory && grimmoryTokenManager.isLoggedIn(it.id) }

    private suspend fun fetchFrom(server: Server): List<GrimmoryNotebook> = coroutineScope {
        val books = allPages { page ->
            grimmoryAppClient.getNotebookBooks(server.url, server.id, page, PAGE_SIZE).getOrThrow()
        }
        books.map { book ->
            async {
                val entries = allPages { page ->
                    grimmoryAppClient.getNotebookEntries(server.url, server.id, book.bookId, page, PAGE_SIZE).getOrThrow()
                }
                GrimmoryNotebook(localBookId = localBookIdFor(server, book), entries = entries)
            }
        }.awaitAll()
    }

    /**
     * The id of Ember's own record of [book]. A book never seen before is added first; its
     * file type is asked for so that it opens in the right reader once downloaded.
     */
    private suspend fun localBookIdFor(server: Server, book: GrimmoryNotebookBook): String {
        bookRepository.getByGrimmoryBookId(book.bookId, server.id)?.let { return it.id }
        val fileType = grimmoryClient.getBookDetail(server.url, server.id, book.bookId).getOrNull()?.primaryFileType
        val summary = GrimmoryBookSummary(
            id = book.bookId,
            title = book.bookTitle,
            authors = book.authors,
            primaryFileType = fileType,
            coverUpdatedOn = book.coverUpdatedOn
        )
        return bookRepository.upsertGrimmorySummaries(server, listOf(summary)).single()
    }

    private suspend fun <T> allPages(fetch: suspend (page: Int) -> GrimmoryAppPage<T>): List<T> {
        val items = mutableListOf<T>()
        var page = 0
        do {
            val result = fetch(page)
            items += result.content
            page++
        } while (result.hasNext && page < MAX_PAGES)
        return items
    }

    private companion object {
        const val PAGE_SIZE = 50

        /** A stop for a server that never says it has run out. */
        const val MAX_PAGES = 50
    }
}
