package com.ember.reader.core.repository

import android.content.Context
import android.graphics.Bitmap
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.serverOrigin
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.sync.PartialMd5
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.readium.r2.shared.publication.services.cover
import timber.log.Timber

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val opdsClient: OpdsClient,
    private val bookOpener: BookOpener,
    private val serverRepository: ServerRepository,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager
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

    fun observeById(id: String): Flow<Book?> = bookDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Book? = bookDao.getById(id)?.toDomain()

    fun search(serverId: Long?, query: String): Flow<List<Book>> =
        bookDao.search(serverId, query).map { entities -> entities.map { it.toDomain() } }

    suspend fun getByOpdsEntryId(opdsEntryId: String, serverId: Long): Book? =
        bookDao.getByOpdsEntryId(opdsEntryId, serverId)?.toDomain()

    suspend fun refreshFromServer(
        server: Server,
        page: Int = 1,
        path: String
    ): Result<OpdsBookPage> {
        val result = opdsClient.fetchBooks(
            baseUrl = server.url,
            username = server.opdsUsername,
            password = server.opdsPassword,
            serverId = server.id,
            path = path,
            page = page
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
                            downloadUrl = book.downloadUrl
                        )
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
        search: String? = null
    ): Result<OpdsBookPage> {
        Timber.d("GrimmoryRefresh: search='$search' seriesName='$seriesName' libraryId=$libraryId shelfId=$shelfId status='$status'")
        val appPage = when {
            seriesName != null -> grimmoryAppClient.getSeriesBooks(server.url, server.id, seriesName, page, size)
            search != null -> grimmoryAppClient.searchBooks(server.url, server.id, search, page, size)
            else -> grimmoryAppClient.getBooks(
                baseUrl = server.url,
                serverId = server.id,
                page = page,
                size = size,
                libraryId = libraryId,
                shelfId = shelfId,
                status = status
            )
        }

        val result = appPage.getOrElse {
            Timber.e(it, "GrimmoryRefresh: API call failed")
            return Result.failure(it)
        }
        Timber.d("GrimmoryRefresh: got ${result.content.size} books (total=${result.totalElements}, hasNext=${result.hasNext})")
        result.content.take(5).forEach { Timber.d("  - '${it.title}' by ${it.authors}") }
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
                        coverUrl = "$origin/api/v1/media/book/${appBook.id}/cover",
                        downloadUrl = "/api/v1/opds/${appBook.id}/download"
                    )
                )
                resolvedIds.add(existing.id)
            } else {
                val book = Book(
                    id = java.util.UUID.randomUUID().toString(),
                    serverId = server.id,
                    opdsEntryId = opdsEntryId,
                    title = appBook.title,
                    author = appBook.authors.firstOrNull(),
                    coverUrl = "$origin/api/v1/media/book/${appBook.id}/cover",
                    downloadUrl = "/api/v1/opds/${appBook.id}/download",
                    format = format,
                    addedAt = Instant.now()
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
                addedAt = Instant.now()
            )
        }

        Timber.d("GrimmoryRefresh: resolvedIds count=${resolvedIds.size}")

        return Result.success(
            OpdsBookPage(
                books = books,
                resolvedBookIds = resolvedIds,
                totalResults = result.totalElements.toInt(),
                nextPagePath = if (result.hasNext) "grimmory:page=${page + 1}" else null
            )
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
        Timber.d("Download: isGrimmory=${server.isGrimmory} loggedIn=${grimmoryTokenManager.isLoggedIn(server.id)} grimmoryBookId=$grimmoryBookId opdsEntryId=${book.opdsEntryId} downloadUrl=$downloadUrl")
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            grimmoryClient.downloadBook(
                baseUrl = server.url,
                serverId = server.id,
                grimmoryBookId = grimmoryBookId,
                destination = file
            ).getOrThrow()
        } else {
            opdsClient.downloadBookToFile(
                baseUrl = server.url,
                username = server.opdsUsername,
                password = server.opdsPassword,
                downloadPath = downloadUrl,
                destination = file
            ).getOrThrow()
        }

        Timber.d("Download complete: file=${file.name} size=${file.length()}")
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

        // Extract and cache cover locally so it works offline
        val metadata = extractMetadata(file)
        if (metadata.coverUrl != null) {
            bookDao.getById(book.id)?.let { entity ->
                bookDao.update(entity.copy(coverUrl = metadata.coverUrl))
            }
        }

        book.copy(
            localPath = file.absolutePath,
            fileHash = fileHash,
            coverUrl = metadata.coverUrl ?: book.coverUrl,
            downloadedAt = Instant.now()
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
                    fileHash = hash
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

    suspend fun cleanupOldDownloads(daysOld: Int = 90): Int {
        val cutoff = Instant.now().minus(java.time.Duration.ofDays(daysOld.toLong()))
        val oldBooks = bookDao.getOldServerDownloads(cutoff)
        var cleaned = 0
        for (entity in oldBooks) {
            entity.localPath?.let { path -> File(path).delete() }
            bookDao.updateLocalPath(entity.id, null, null)
            cleaned++
        }
        if (cleaned > 0) Timber.d("Auto-cleanup: removed $cleaned old downloads")
        return cleaned
    }

    data class RelinkMatch(
        val serverBookId: String,
        val serverName: String,
        val serverId: Long
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
                if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                    runCatching { refreshFromGrimmory(server) }
                        .onFailure { Timber.w(it, "Relink: Grimmory catalog fetch failed") }
                } else {
                    val catalogPath = server.url.trimEnd('/') + "/catalog"
                    val pathPart = catalogPath.substringAfter(
                        com.ember.reader.core.network.serverOrigin(server.url)
                    )
                    runCatching { refreshFromServer(server, path = pathPart) }
                        .onFailure { Timber.w(it, "Relink: OPDS catalog fetch failed") }
                }
            }

            // Strategy 1: hash or title match in local DB
            val match = bookDao.getByServerAndHashOrTitle(server.id, hash, book.title)
            if (match != null && match.id != book.id) {
                Timber.d("Relink: found match via DB: '${match.title}' (${match.id})")
                return@withContext RelinkMatch(match.id, server.name, server.id)
            }

            // Strategy 2: search Grimmory API by title (server-side fuzzy search)
            if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                Timber.d("Relink: trying Grimmory search for '${book.title}'")
                val searchResult = grimmoryAppClient.searchBooks(
                    baseUrl = server.url,
                    serverId = server.id,
                    query = book.title,
                    size = 5
                ).getOrNull()

                if (searchResult != null) {
                    for (appBook in searchResult.content) {
                        // Match by exact title or author+title combo
                        val titleMatch = appBook.title.equals(book.title, ignoreCase = true) ||
                            appBook.title.contains(book.title, ignoreCase = true) ||
                            book.title.contains(appBook.title, ignoreCase = true)
                        if (titleMatch) {
                            // Ensure this book exists in our DB, create if not
                            val opdsEntryId = "urn:booklore:book:${appBook.id}"
                            val existing = bookDao.getByOpdsEntryId(opdsEntryId, server.id)
                            val matchId = if (existing != null) {
                                existing.id
                            } else {
                                val origin = com.ember.reader.core.network.serverOrigin(server.url)
                                val newBook = Book(
                                    id = java.util.UUID.randomUUID().toString(),
                                    serverId = server.id,
                                    opdsEntryId = opdsEntryId,
                                    title = appBook.title,
                                    author = appBook.authors.firstOrNull(),
                                    coverUrl = "$origin/api/v1/media/book/${appBook.id}/cover",
                                    downloadUrl = "/api/v1/opds/${appBook.id}/download",
                                    format = when (appBook.primaryFileType?.uppercase()) {
                                        "PDF" -> BookFormat.PDF
                                        else -> BookFormat.EPUB
                                    },
                                    addedAt = java.time.Instant.now()
                                )
                                bookDao.insert(newBook.toEntity())
                                newBook.id
                            }
                            if (matchId != book.id) {
                                Timber.d("Relink: found match via Grimmory search: '${appBook.title}' (${appBook.id})")
                                return@withContext RelinkMatch(matchId, server.name, server.id)
                            }
                        }
                    }
                }
            }

            val currentBooks = if (serverBooks.isEmpty()) bookDao.observeByServer(server.id).first() else serverBooks
            Timber.d("Relink: no match found. ${currentBooks.size} books on server. Looking for '${book.title}'")

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
    suspend fun recoverOrphanedFiles(): Int =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                        downloadedAt = Instant.now()
                    )
                    bookDao.insert(book.toEntity())
                    Timber.i("Recovered orphan: created entry for '$title' from ${file.name}")
                    recovered++
                }
            }

            recovered
        }

    data class BookMetadata(
        val title: String,
        val author: String?,
        val coverUrl: String?,
        val publisher: String? = null,
        val language: String? = null,
        val subjects: String? = null,
        val pageCount: Int? = null,
        val publishedDate: String? = null,
        val description: String? = null
    )

    suspend fun extractMetadata(file: File): BookMetadata {
        val publication = bookOpener.open(file).getOrNull()
            ?: return BookMetadata(file.nameWithoutExtension, null, null)

        val meta = publication.metadata
        val title = meta.title?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val author = meta.authors.firstOrNull()?.name
        val publisher = meta.publishers.firstOrNull()?.name
        val language = meta.languages.firstOrNull()
        val subjects = meta.subjects.map { it.name }.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val pageCount = meta.numberOfPages
        val publishedDate = meta.published?.toString()
        val description = meta.description

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
        return BookMetadata(title, author, coverUrl, publisher, language, subjects, pageCount, publishedDate, description)
    }

    /** Update a book's metadata from its embedded file data (called after download). */
    suspend fun enrichBookMetadata(bookId: String) {
        val entity = bookDao.getById(bookId) ?: return
        val localPath = entity.localPath ?: return
        val metadata = extractMetadata(File(localPath))
        bookDao.update(
            entity.copy(
                publisher = metadata.publisher ?: entity.publisher,
                language = metadata.language ?: entity.language,
                subjects = metadata.subjects ?: entity.subjects,
                pageCount = metadata.pageCount ?: entity.pageCount,
                publishedDate = metadata.publishedDate ?: entity.publishedDate,
                description = metadata.description ?: entity.description,
                coverUrl = metadata.coverUrl ?: entity.coverUrl
            )
        )
    }
}
