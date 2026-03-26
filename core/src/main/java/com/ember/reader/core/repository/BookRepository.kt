package com.ember.reader.core.repository

import android.content.Context
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.grimmory.GrimmoryAppBook
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryAppPage
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.network.serverOrigin
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.sync.PartialMd5
import org.readium.r2.shared.publication.services.cover
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.graphics.Bitmap
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val opdsClient: OpdsClient,
    private val bookOpener: BookOpener,
    private val serverRepository: ServerRepository,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
) {

    private val booksDir: File by lazy {
        File(context.filesDir, "books").also { it.mkdirs() }
    }

    private val coversDir: File by lazy {
        File(context.filesDir, "covers").also { it.mkdirs() }
    }

    fun observeByServer(serverId: Long): Flow<List<Book>> =
        bookDao.observeByServer(serverId).map { entities -> entities.map { it.toDomain() } }

    fun observeLocalBooks(): Flow<List<Book>> =
        bookDao.observeLocalBooks().map { entities -> entities.map { it.toDomain() } }

    fun observeDownloadedBooks(): Flow<List<Book>> =
        bookDao.observeDownloadedBooks().map { entities -> entities.map { it.toDomain() } }

    fun observeServerDownloads(): Flow<List<Book>> =
        bookDao.observeServerDownloads().map { entities -> entities.map { it.toDomain() } }

    suspend fun getDownloadedBooksForServer(serverId: Long): List<Book> =
        bookDao.getDownloadedBooksForServer(serverId).map { it.toDomain() }

    fun observeRecentlyReading(): Flow<List<Book>> =
        bookDao.observeRecentlyReading().map { entities -> entities.map { it.toDomain() } }

    fun observeById(id: String): Flow<Book?> =
        bookDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Book? =
        bookDao.getById(id)?.toDomain()

    fun search(serverId: Long?, query: String): Flow<List<Book>> =
        bookDao.search(serverId, query).map { entities -> entities.map { it.toDomain() } }

    suspend fun refreshFromServer(
        server: Server,
        page: Int = 1,
        path: String,
    ): Result<OpdsBookPage> {
        val result = opdsClient.fetchBooks(
            baseUrl = server.url,
            username = server.opdsUsername,
            password = server.opdsPassword,
            serverId = server.id,
            path = path,
            page = page,
        )
        val resolvedIds = mutableListOf<String>()
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
                    resolvedIds.add(existing.id)
                } else {
                    bookDao.insert(book.toEntity())
                    resolvedIds.add(book.id)
                }
            }
        }
        return result.map { it.copy(resolvedBookIds = resolvedIds) }
    }

    /**
     * Refreshes book list from Grimmory's App API instead of OPDS.
     * Returns the same OpdsBookPage structure for UI compatibility.
     */
    suspend fun refreshFromGrimmory(
        server: Server,
        page: Int = 0,
        size: Int = 50,
        libraryId: Long? = null,
        shelfId: Long? = null,
        seriesName: String? = null,
        status: String? = null,
        search: String? = null,
    ): Result<OpdsBookPage> {
        val appPage = if (seriesName != null) {
            grimmoryAppClient.getSeriesBooks(server.url, server.id, seriesName, page, size)
        } else {
            grimmoryAppClient.getBooks(
                baseUrl = server.url,
                serverId = server.id,
                page = page,
                size = size,
                libraryId = libraryId,
                shelfId = shelfId,
                status = status,
                search = search,
            )
        }

        val result = appPage.getOrElse { return Result.failure(it) }
        val origin = serverOrigin(server.url)
        val resolvedIds = mutableListOf<String>()

        for (appBook in result.content) {
            val opdsEntryId = "urn:booklore:book:${appBook.id}"
            val existing = bookDao.getByOpdsEntryId(opdsEntryId, server.id)

            val format = when (appBook.primaryFileType?.uppercase()) {
                "PDF" -> BookFormat.PDF
                "AUDIOBOOK" -> BookFormat.AUDIOBOOK
                else -> BookFormat.EPUB
            }

            if (existing != null) {
                bookDao.update(
                    existing.copy(
                        title = appBook.title,
                        author = appBook.authors.firstOrNull(),
                        coverUrl = "$origin/api/v1/opds/${appBook.id}/cover",
                        downloadUrl = "/api/v1/opds/${appBook.id}/download",
                    ),
                )
                resolvedIds.add(existing.id)
            } else {
                val book = Book(
                    id = java.util.UUID.randomUUID().toString(),
                    serverId = server.id,
                    opdsEntryId = opdsEntryId,
                    title = appBook.title,
                    author = appBook.authors.firstOrNull(),
                    coverUrl = "$origin/api/v1/opds/${appBook.id}/cover",
                    downloadUrl = "/api/v1/opds/${appBook.id}/download",
                    format = format,
                    addedAt = Instant.now(),
                )
                bookDao.insert(book.toEntity())
                resolvedIds.add(book.id)
            }
        }

        val books = result.content.map { appBook ->
            Book(
                id = "",
                serverId = server.id,
                opdsEntryId = "urn:booklore:book:${appBook.id}",
                title = appBook.title,
                author = appBook.authors.firstOrNull(),
                format = BookFormat.EPUB,
                addedAt = Instant.now(),
            )
        }

        return Result.success(
            OpdsBookPage(
                books = books,
                resolvedBookIds = resolvedIds,
                totalResults = result.totalElements.toInt(),
                nextPagePath = if (result.hasNext) "grimmory:page=${page + 1}" else null,
            ),
        )
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

        val grimmoryBookId = book.grimmoryBookId
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            grimmoryClient.downloadBook(
                baseUrl = server.url,
                serverId = server.id,
                grimmoryBookId = grimmoryBookId,
                destination = file,
            ).getOrThrow()
        } else {
            opdsClient.downloadBookToFile(
                baseUrl = server.url,
                username = server.opdsUsername,
                password = server.opdsPassword,
                downloadPath = downloadUrl,
                destination = file,
            ).getOrThrow()
        }

        if (file.length() < 100) {
            file.delete()
            error("Downloaded file is too small — server may have returned an error")
        }
        val header = ByteArray(5).also { buf -> file.inputStream().use { it.read(buf) } }
        if (header.decodeToString().startsWith("<!") || header.decodeToString().startsWith("<html")) {
            file.delete()
            error("Downloaded file is HTML, not a book — check server URL and credentials")
        }

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
        val file = book.localPath?.let { File(it) }

        // Extract metadata and cover from the file
        val enrichedBook = if (file != null && file.exists()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val metadata = extractMetadata(file)
                val hash = PartialMd5.compute(file)
                book.copy(
                    title = if (book.title == "Untitled" || book.title == file.nameWithoutExtension) {
                        metadata.title
                    } else {
                        book.title
                    },
                    author = book.author ?: metadata.author,
                    coverUrl = metadata.coverUrl,
                    fileHash = hash,
                )
            }
        } else {
            book
        }

        bookDao.insert(enrichedBook.toEntity())
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

    data class RelinkMatch(
        val serverBookId: String,
        val serverName: String,
        val serverId: Long,
    )

    /**
     * Finds server books that match a local/orphaned book by title.
     * Returns all matches so the UI can let the user choose if multiple.
     */
    suspend fun findRelinkMatches(bookId: String, serverId: Long): RelinkMatch? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val book = bookDao.getById(bookId)?.toDomain() ?: return@withContext null
            val server = serverRepository.getById(serverId) ?: return@withContext null

            Timber.d("Relink: searching server '${server.name}' for '${book.title}'")

            val hash = book.fileHash ?: book.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) PartialMd5.compute(file) else null
            } ?: ""

            // Check if we have any books for this server, fetch catalog if not
            val serverBooks = bookDao.observeByServer(server.id).first()
            if (serverBooks.isEmpty()) {
                Timber.d("Relink: no books cached for server, fetching catalog...")
                val catalogPath = server.url.trimEnd('/') + "/catalog"
                val pathPart = catalogPath.substringAfter(
                    com.ember.reader.core.network.serverOrigin(server.url),
                )
                runCatching { refreshFromServer(server, path = pathPart) }
                    .onFailure { Timber.w(it, "Relink: catalog fetch failed") }
            }

            // Try hash or title match
            val match = bookDao.getByServerAndHashOrTitle(server.id, hash, book.title)
            if (match != null && match.id != book.id) {
                Timber.d("Relink: found match: '${match.title}' (${match.id})")
                return@withContext RelinkMatch(match.id, server.name, server.id)
            }

            val currentBooks = if (serverBooks.isEmpty()) bookDao.observeByServer(server.id).first() else serverBooks
            Timber.d("Relink: no match. ${currentBooks.size} books on server. Looking for '${book.title}'")
            currentBooks.take(10).forEach { Timber.d("  - '${it.title}'") }

            null
        }

    /**
     * Relinks a local/orphaned book to a specific server book.
     * Transfers the local file path and hash, then removes the orphan entry.
     */
    suspend fun relinkToServerBook(localBookId: String, serverBookId: String): String {
        val localBook = bookDao.getById(localBookId)?.toDomain()
            ?: return "Book not found"
        val localPath = localBook.localPath
            ?: return "Book has no local file"

        bookDao.updateLocalPath(serverBookId, localPath, localBook.downloadedAt)
        localBook.fileHash?.let { bookDao.updateFileHash(serverBookId, it) }
        bookDao.deleteById(localBookId)
        return "Book relinked"
    }

    /**
     * Scans the books directory for files that aren't tracked in the database.
     * - If a file's hash matches an existing book (e.g., re-linked after server deletion),
     *   updates that book's localPath.
     * - If no match, creates a new local book entry from the file.
     * Returns the number of recovered books.
     */
    suspend fun recoverOrphanedFiles(): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val trackedPaths = bookDao.observeDownloadedBooks().first()
            .mapNotNull { it.localPath }
            .toSet()

        var recovered = 0
        val files = booksDir.listFiles() ?: return@withContext 0

        for (file in files) {
            if (file.absolutePath in trackedPaths) continue
            if (!file.isFile) continue

            val extension = file.extension.lowercase()
            val format = when (extension) {
                "epub" -> BookFormat.EPUB
                "pdf" -> BookFormat.PDF
                else -> continue
            }

            val hash = PartialMd5.compute(file)

            // Try to match by hash (book exists in DB but localPath was cleared)
            val existingByHash = bookDao.getByFileHash(hash)
            if (existingByHash != null && existingByHash.localPath == null) {
                bookDao.updateLocalPath(existingByHash.id, file.absolutePath, Instant.now())
                Timber.i("Recovered orphan: re-linked ${file.name} to ${existingByHash.title}")
                recovered++
                continue
            }

            // No match — extract metadata and create a local book
            if (existingByHash == null) {
                val (title, author, coverUrl) = extractMetadata(file)
                val book = Book(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    author = author,
                    format = format,
                    localPath = file.absolutePath,
                    fileHash = hash,
                    coverUrl = coverUrl,
                    addedAt = Instant.now(),
                    downloadedAt = Instant.now(),
                )
                bookDao.insert(book.toEntity())
                Timber.i("Recovered orphan: created entry for '$title' from ${file.name}")
                recovered++
            }
        }

        recovered
    }

    private data class BookMetadata(
        val title: String,
        val author: String?,
        val coverUrl: String?,
    )

    private suspend fun extractMetadata(file: File): BookMetadata {
        val publication = bookOpener.open(file).getOrNull()
            ?: return BookMetadata(file.nameWithoutExtension, null, null)

        val title = publication.metadata.title?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val author = publication.metadata.authors.firstOrNull()?.name

        // Extract and save cover image
        val coverUrl = try {
            val bitmap = publication.cover()
            if (bitmap != null) {
                val coverFile = File(coversDir, "${file.nameWithoutExtension}.jpg")
                FileOutputStream(coverFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                coverFile.toURI().toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract cover from ${file.name}")
            null
        }

        publication.close()
        return BookMetadata(title, author, coverUrl)
    }
}
