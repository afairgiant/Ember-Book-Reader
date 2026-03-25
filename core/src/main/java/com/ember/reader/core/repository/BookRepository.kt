package com.ember.reader.core.repository

import android.content.Context
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.sync.PartialMd5
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val opdsClient: OpdsClient,
) {

    private val booksDir: File by lazy {
        File(context.filesDir, "books").also { it.mkdirs() }
    }

    fun observeByServer(serverId: Long): Flow<List<Book>> =
        bookDao.observeByServer(serverId).map { entities -> entities.map { it.toDomain() } }

    fun observeLocalBooks(): Flow<List<Book>> =
        bookDao.observeLocalBooks().map { entities -> entities.map { it.toDomain() } }

    fun observeDownloadedBooks(): Flow<List<Book>> =
        bookDao.observeDownloadedBooks().map { entities -> entities.map { it.toDomain() } }

    fun observeById(id: String): Flow<Book?> =
        bookDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Book? =
        bookDao.getById(id)?.toDomain()

    fun search(serverId: Long?, query: String): Flow<List<Book>> =
        bookDao.search(serverId, query).map { entities -> entities.map { it.toDomain() } }

    suspend fun refreshFromServer(
        server: Server,
        page: Int = 1,
        path: String = "/api/v1/opds/catalog",
    ): Result<OpdsBookPage> {
        val result = opdsClient.fetchBooks(
            baseUrl = server.url,
            username = server.opdsUsername,
            password = server.opdsPassword,
            serverId = server.id,
            path = path,
            page = page,
        )
        result.onSuccess { bookPage ->
            for (book in bookPage.books) {
                val existing = book.opdsEntryId?.let {
                    bookDao.getByOpdsEntryId(it, server.id)
                }
                if (existing != null) {
                    bookDao.update(
                        existing.copy(
                            title = book.title,
                            author = book.author,
                            description = book.description,
                            coverUrl = book.coverUrl,
                            downloadUrl = book.downloadUrl,
                        ),
                    )
                } else {
                    bookDao.insert(book.toEntity())
                }
            }
        }
        return result
    }

    suspend fun downloadBook(book: Book, server: Server): Result<Book> = runCatching {
        val downloadUrl = book.downloadUrl
            ?: error("No download URL for book: ${book.title}")

        val extension = when (book.format) {
            com.ember.reader.core.model.BookFormat.EPUB -> "epub"
            com.ember.reader.core.model.BookFormat.PDF -> "pdf"
            com.ember.reader.core.model.BookFormat.AUDIOBOOK -> "m4b"
        }
        val fileName = "${book.id}.$extension"
        val file = File(booksDir, fileName)

        opdsClient.downloadBookToFile(
            baseUrl = server.url,
            username = server.opdsUsername,
            password = server.opdsPassword,
            downloadPath = downloadUrl,
            destination = file,
        ).getOrThrow()

        val fileHash = PartialMd5.compute(file)
        bookDao.updateLocalPath(book.id, file.absolutePath, Instant.now())
        bookDao.updateFileHash(book.id, fileHash)

        book.copy(
            localPath = file.absolutePath,
            fileHash = fileHash,
            downloadedAt = Instant.now(),
        )
    }

    suspend fun addLocalBook(book: Book) {
        bookDao.insert(book.toEntity())
        book.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val hash = PartialMd5.compute(file)
                bookDao.updateFileHash(book.id, hash)
            }
        }
    }

    suspend fun deleteBook(bookId: String) {
        val book = bookDao.getById(bookId)
        book?.localPath?.let { path ->
            File(path).delete()
        }
        bookDao.deleteById(bookId)
    }

    suspend fun removeDownload(bookId: String) {
        val book = bookDao.getById(bookId)
        book?.localPath?.let { path ->
            File(path).delete()
        }
        bookDao.updateLocalPath(bookId, null, null)
    }
}
