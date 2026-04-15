package com.ember.reader.core.repository

import android.content.Context
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.grimmory.GrimmoryAppBook
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.paging.GrimmoryRequest
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.DownloadProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.serverOrigin
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.sync.PartialMd5
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val opdsClient: OpdsClient,
    private val serverRepository: ServerRepository,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val bookDownloader: BookDownloader,
    private val metadataExtractor: BookMetadataExtractor
) {

    private val booksDir: File by lazy {
        File(context.filesDir, "books").also { it.mkdirs() }
    }

    private val cachePrefs by lazy {
        context.getSharedPreferences("book_cache", android.content.Context.MODE_PRIVATE)
    }

    fun cacheRecentlyAddedIds(ids: List<String>) {
        cachePrefs.edit().putString("recently_added_ids", ids.joinToString(",")).apply()
    }

    fun getCachedRecentlyAddedIds(): List<String> {
        val raw = cachePrefs.getString("recently_added_ids", null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
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

    /** Batch lookup for UI flows that already hold a selection of book IDs (e.g. paged lists). */
    suspend fun getByIds(ids: Set<String>): List<Book> {
        if (ids.isEmpty()) return emptyList()
        return bookDao.getByIds(ids).map { it.toDomain() }
    }

    fun search(serverId: Long?, query: String): Flow<List<Book>> =
        bookDao.search(serverId, query).map { entities -> entities.map { it.toDomain() } }

    suspend fun getByOpdsEntryId(opdsEntryId: String, serverId: Long): Book? =
        bookDao.getByOpdsEntryId(opdsEntryId, serverId)?.toDomain()

    /** Ensures a book metadata entry exists in the local DB. Returns the book's local ID. */
    suspend fun ensureBookExists(
        serverId: Long,
        opdsEntryId: String,
        title: String,
        author: String?,
        coverUrl: String?,
        format: BookFormat
    ): String {
        val existing = bookDao.getByOpdsEntryId(opdsEntryId, serverId)
        if (existing != null) return existing.id
        val book = Book(
            id = java.util.UUID.randomUUID().toString(),
            serverId = serverId,
            opdsEntryId = opdsEntryId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            format = format
        )
        bookDao.insert(book.toEntity())
        return book.id
    }

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
        size: Int = 100,
        libraryId: Long? = null,
        shelfId: Long? = null,
        magicShelfId: Long? = null,
        seriesName: String? = null,
        status: String? = null,
        search: String? = null,
        sort: String = "addedOn",
        dir: String = "desc",
        minRating: Int? = null,
        maxRating: Int? = null,
        authors: String? = null,
        language: String? = null
    ): Result<OpdsBookPage> = upsertGrimmoryPage(
        server = server,
        request = GrimmoryRequest(
            libraryId = libraryId,
            shelfId = shelfId,
            magicShelfId = magicShelfId,
            seriesName = seriesName,
            status = status,
            search = search,
            sort = sort,
            dir = dir,
            minRating = minRating,
            maxRating = maxRating,
            authors = authors,
            language = language,
        ),
        page = page,
        pageSize = size,
    )

    /**
     * Fetches one Grimmory page and upserts its books into the local DB. Returns an OpdsBookPage
     * whose `resolvedBookIds` are the just-seen local IDs (in server order) and whose
     * `nextPagePath` is a `grimmory:page=N+1` cursor (or null at the last page).
     *
     * This is the single code path behind both [refreshFromGrimmory] and the library paging
     * mediator — any tweak to the upsert contract should happen here.
     */
    suspend fun upsertGrimmoryPage(
        server: Server,
        request: GrimmoryRequest,
        page: Int,
        pageSize: Int = 100,
    ): Result<OpdsBookPage> {
        Timber.d(
            "GrimmoryRefresh: search='${request.search}' seriesName='${request.seriesName}' " +
                "libraryId=${request.libraryId} shelfId=${request.shelfId} " +
                "magicShelfId=${request.magicShelfId} status='${request.status}' " +
                "sort='${request.sort}' dir='${request.dir}' " +
                "minRating=${request.minRating} maxRating=${request.maxRating} " +
                "authors='${request.authors}' language='${request.language}'",
        )
        val appPage = when {
            request.seriesName != null -> grimmoryAppClient.getSeriesBooks(
                server.url, server.id, request.seriesName, page, pageSize,
            )
            request.search != null -> grimmoryAppClient.searchBooks(
                server.url, server.id, request.search, page, pageSize,
            )
            request.magicShelfId != null -> grimmoryAppClient.getMagicShelfBooks(
                server.url, server.id, request.magicShelfId, page, pageSize,
            )
            else -> grimmoryAppClient.getBooks(
                baseUrl = server.url,
                serverId = server.id,
                page = page,
                size = pageSize,
                sort = request.sort,
                dir = request.dir,
                libraryId = request.libraryId,
                shelfId = request.shelfId,
                status = request.status,
                minRating = request.minRating,
                maxRating = request.maxRating,
                authors = request.authors,
                language = request.language,
            )
        }

        val result = appPage.getOrElse {
            Timber.e(it, "GrimmoryRefresh: API call failed")
            return Result.failure(it)
        }
        Timber.d(
            "GrimmoryRefresh: got ${result.content.size} books " +
                "(total=${result.totalElements}, hasNext=${result.hasNext})",
        )
        result.content.take(5).forEach { Timber.d("  - '${it.title}' by ${it.authors}") }
        val origin = serverOrigin(server.url)
        val resolvedIds = mutableListOf<String>()

        for (appBook in result.content) {
            resolvedIds.add(
                upsertGrimmoryBook(server.id, origin, grimmoryOpdsEntryId(appBook.id), appBook),
            )
        }

        return Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = resolvedIds,
                totalResults = result.totalElements.toInt(),
                nextPagePath = if (result.hasNext) "grimmory:page=${page + 1}" else null,
            ),
        )
    }

    /**
     * Walks every page of the unfiltered Grimmory catalog, upserting books as it goes, then prunes
     * server-scoped rows whose opdsEntryId wasn't seen. Downloaded stale books are detached
     * (serverId = NULL) so the file remains in the local library. Undownloaded stale books are
     * deleted outright.
     *
     * If any page fetch fails, the reconcile aborts WITHOUT deleting anything — a partial fetch
     * would falsely flag books as missing.
     */
    suspend fun reconcileGrimmoryLibrary(server: Server): Result<Int> = runCatching {
        val seenOpdsIds = mutableSetOf<String>()
        val origin = serverOrigin(server.url)
        var page = 0
        val pageSize = 200

        while (true) {
            val appPage = grimmoryAppClient.getBooks(
                baseUrl = server.url,
                serverId = server.id,
                page = page,
                size = pageSize
            ).getOrElse { throw it }

            for (appBook in appPage.content) {
                val opdsEntryId = grimmoryOpdsEntryId(appBook.id)
                seenOpdsIds.add(opdsEntryId)
                upsertGrimmoryBook(server.id, origin, opdsEntryId, appBook)
            }

            if (!appPage.hasNext) break
            page++
        }

        pruneStaleServerBooks(server.id, seenOpdsIds)
    }

    /**
     * Walks every page of an OPDS catalog path, upserting books as it goes, then prunes stale rows
     * the same way as [reconcileGrimmoryLibrary].
     */
    suspend fun reconcileOpdsLibrary(server: Server, rootPath: String): Result<Int> = runCatching {
        val seenOpdsIds = mutableSetOf<String>()
        var currentPath: String? = rootPath
        var page = 1

        while (currentPath != null) {
            val bookPage = opdsClient.fetchBooks(
                baseUrl = server.url,
                username = server.opdsUsername,
                password = server.opdsPassword,
                serverId = server.id,
                path = currentPath,
                page = page
            ).getOrElse { throw it }

            for (book in bookPage.books) {
                val opdsEntryId = book.opdsEntryId ?: continue
                seenOpdsIds.add(opdsEntryId)
                val existing = bookDao.getByOpdsEntryId(opdsEntryId, server.id)
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
                } else {
                    bookDao.insert(book.toEntity())
                }
            }

            if (bookPage.nextPagePath == null) break
            page++
        }

        pruneStaleServerBooks(server.id, seenOpdsIds)
    }

    /** Upserts a Grimmory book into the local DB. Returns the local book ID. */
    private suspend fun upsertGrimmoryBook(
        serverId: Long,
        origin: String,
        opdsEntryId: String,
        appBook: GrimmoryAppBook
    ): String {
        val existing = bookDao.getByOpdsEntryId(opdsEntryId, serverId)
        val format = appBook.toBookFormat()
        val coverUrl = resolvedCoverUrl(origin, appBook, format)
        val downloadUrl = grimmoryDownloadUrl(appBook.id)

        if (existing != null) {
            bookDao.update(
                existing.copy(
                    title = appBook.title,
                    author = appBook.authors.firstOrNull(),
                    coverUrl = coverUrl,
                    downloadUrl = downloadUrl,
                    series = appBook.seriesName,
                    seriesIndex = appBook.seriesNumber
                )
            )
            return existing.id
        } else {
            val book = Book(
                id = java.util.UUID.randomUUID().toString(),
                serverId = serverId,
                opdsEntryId = opdsEntryId,
                title = appBook.title,
                author = appBook.authors.firstOrNull(),
                coverUrl = coverUrl,
                downloadUrl = downloadUrl,
                format = format,
                series = appBook.seriesName,
                seriesIndex = appBook.seriesNumber,
                addedAt = Instant.now()
            )
            bookDao.insert(book.toEntity())
            return book.id
        }
    }

    /**
     * Given the set of opdsEntryIds seen on the server, deletes undownloaded stale rows and
     * detaches downloaded stale rows (serverId -> NULL). Returns the total number of rows
     * affected.
     */
    private suspend fun pruneStaleServerBooks(serverId: Long, seenOpdsIds: Set<String>): Int {
        val cachedIds = bookDao.getOpdsEntryIdsForServer(serverId)
        val staleIds = cachedIds.filter { it !in seenOpdsIds }
        if (staleIds.isEmpty()) {
            Timber.d("Reconcile: no stale books for server=$serverId")
            return 0
        }
        val allServerBooks = bookDao.getBooksByServerId(serverId).associateBy { it.opdsEntryId }
        val (downloadedStale, undownloadedStale) = staleIds.partition { id ->
            allServerBooks[id]?.localPath != null
        }
        if (undownloadedStale.isNotEmpty()) {
            bookDao.deleteServerBooksByOpdsEntryIds(serverId, undownloadedStale)
        }
        if (downloadedStale.isNotEmpty()) {
            bookDao.detachServerAssociation(serverId, downloadedStale)
        }
        Timber.d(
            "Reconcile: server=$serverId pruned=${undownloadedStale.size} detached=${downloadedStale.size}"
        )
        return staleIds.size
    }

    suspend fun downloadBook(
        book: Book,
        server: Server,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Result<Book> = bookDownloader.downloadBook(book, server, onProgress)

    suspend fun addLocalBook(book: Book) {
        val file = book.localPath?.let { File(it) }

        // Extract metadata and cover from the file
        val enrichedBook = if (file != null && file.exists()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val metadata = metadataExtractor.extractMetadata(file)
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
                    val origin = serverOrigin(server.url)
                    for (appBook in searchResult.content) {
                        val titleMatch = appBook.title.equals(book.title, ignoreCase = true) ||
                            appBook.title.contains(book.title, ignoreCase = true) ||
                            book.title.contains(appBook.title, ignoreCase = true)
                        if (titleMatch) {
                            val opdsEntryId = grimmoryOpdsEntryId(appBook.id)
                            val matchId =
                                upsertGrimmoryBook(server.id, origin, opdsEntryId, appBook)
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
                    val (title, author, coverUrl) = metadataExtractor.extractMetadata(file)
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

    /** Kept for callers that reference it — delegates to BookMetadataExtractor. */
    suspend fun extractMetadata(file: File): BookMetadata = metadataExtractor.extractMetadata(file)

    /** Update just the cover URL on an existing book row. Used after applying a new cover. */
    suspend fun updateBookCoverUrl(bookId: String, coverUrl: String) {
        val entity = bookDao.getById(bookId) ?: return
        bookDao.update(entity.copy(coverUrl = coverUrl))
    }

    /** Update local book metadata fields from user edits. DB only — does not modify the file. */
    suspend fun updateLocalBookMetadata(
        bookId: String,
        title: String,
        author: String?,
        description: String?,
        series: String?,
        seriesIndex: Float?,
        publisher: String?,
        language: String?,
        subjects: String?,
        pageCount: Int?,
        publishedDate: String?
    ) {
        val entity = bookDao.getById(bookId) ?: return
        bookDao.update(
            entity.copy(
                title = title,
                author = author,
                description = description,
                series = series,
                seriesIndex = seriesIndex,
                publisher = publisher,
                language = language,
                subjects = subjects,
                pageCount = pageCount,
                publishedDate = publishedDate
            )
        )
    }

    /** Update a book's metadata from its embedded file data (called after download). */
    suspend fun enrichBookMetadata(bookId: String) {
        val entity = bookDao.getById(bookId) ?: return
        val localPath = entity.localPath ?: return
        val metadata = metadataExtractor.extractMetadata(File(localPath))
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

    private fun grimmoryOpdsEntryId(grimmoryBookId: Long): String =
        "urn:booklore:book:$grimmoryBookId"

    private fun grimmoryDownloadUrl(grimmoryBookId: Long): String =
        "/api/v1/opds/$grimmoryBookId/download"

    private fun resolvedCoverUrl(
        baseUrl: String,
        appBook: GrimmoryAppBook,
        format: BookFormat
    ): String = if (format == BookFormat.AUDIOBOOK) {
        grimmoryAppClient.audiobookCoverUrl(baseUrl, appBook.id, appBook.coverUpdatedOn)
    } else {
        grimmoryAppClient.coverUrl(baseUrl, appBook.id, appBook.coverUpdatedOn)
    }
}

private fun GrimmoryAppBook.toBookFormat(): BookFormat = when (primaryFileType?.uppercase()) {
    "PDF" -> BookFormat.PDF
    "AUDIOBOOK" -> BookFormat.AUDIOBOOK
    else -> BookFormat.EPUB
}
