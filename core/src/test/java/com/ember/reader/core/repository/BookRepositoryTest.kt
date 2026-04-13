package com.ember.reader.core.repository

import android.content.Context
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.opds.OpdsClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir

@ExtendWith(MockKExtension::class)
class BookRepositoryTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var bookDao: BookDao

    @MockK
    private lateinit var opdsClient: OpdsClient

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var grimmoryAppClient: GrimmoryAppClient

    @MockK
    private lateinit var grimmoryTokenManager: GrimmoryTokenManager

    @MockK
    private lateinit var bookDownloader: BookDownloader

    @MockK
    private lateinit var metadataExtractor: BookMetadataExtractor

    @TempDir
    lateinit var tempDir: File

    private lateinit var repository: BookRepository

    private val testServer = Server(
        id = 1L,
        name = "Test Server",
        url = "http://localhost/api/v1/opds",
        opdsUsername = "user",
        opdsPassword = "pass",
        kosyncUsername = "kuser",
        kosyncPassword = "kpass"
    )

    private val catalogPath = "/api/v1/opds/catalog"

    @BeforeEach
    fun setUp() {
        every { context.filesDir } returns tempDir
        repository = BookRepository(context, bookDao, opdsClient, serverRepository, grimmoryAppClient, grimmoryTokenManager, bookDownloader, metadataExtractor)
    }

    @Test
    fun `refreshFromServer inserts new books`() = runTest {
        val newBook = Book(
            id = "new-book-1",
            serverId = 1L,
            opdsEntryId = "opds-entry-1",
            title = "New Book",
            author = "Author",
            format = BookFormat.EPUB,
            addedAt = Instant.now()
        )
        val bookPage = OpdsBookPage(books = listOf(newBook))

        coEvery {
            opdsClient.fetchBooks(
                baseUrl = testServer.url,
                username = testServer.opdsUsername,
                password = testServer.opdsPassword,
                serverId = testServer.id,
                path = catalogPath,
                page = 1
            )
        } returns Result.success(bookPage)
        coEvery { bookDao.getByOpdsEntryId("opds-entry-1", 1L) } returns null
        coEvery { bookDao.insert(any()) } returns Unit

        val result = repository.refreshFromServer(testServer, path = catalogPath)

        assertTrue(result.isSuccess)
        coVerify { bookDao.insert(any()) }
        coVerify(exactly = 0) { bookDao.update(any()) }
    }

    @Test
    fun `refreshFromServer updates existing books by opdsEntryId`() = runTest {
        val existingEntity = BookEntity(
            id = "existing-book-1",
            serverId = 1L,
            opdsEntryId = "opds-entry-1",
            title = "Old Title",
            author = "Old Author",
            format = BookFormat.EPUB,
            addedAt = Instant.now()
        )
        val updatedBook = Book(
            id = "some-new-id",
            serverId = 1L,
            opdsEntryId = "opds-entry-1",
            title = "Updated Title",
            author = "Updated Author",
            format = BookFormat.EPUB,
            addedAt = Instant.now()
        )
        val bookPage = OpdsBookPage(books = listOf(updatedBook))

        coEvery {
            opdsClient.fetchBooks(
                baseUrl = testServer.url,
                username = testServer.opdsUsername,
                password = testServer.opdsPassword,
                serverId = testServer.id,
                path = catalogPath,
                page = 1
            )
        } returns Result.success(bookPage)
        coEvery { bookDao.getByOpdsEntryId("opds-entry-1", 1L) } returns existingEntity
        coEvery { bookDao.update(any()) } returns Unit

        val result = repository.refreshFromServer(testServer, path = catalogPath)

        assertTrue(result.isSuccess)
        coVerify {
            bookDao.update(
                match { entity ->
                    entity.id == "existing-book-1" &&
                        entity.title == "Updated Title" &&
                        entity.author == "Updated Author"
                }
            )
        }
        coVerify(exactly = 0) { bookDao.insert(any()) }
    }

    @Test
    fun `addLocalBook computes file hash`() = runTest {
        val bookFile = File(tempDir, "test-book.epub").apply {
            writeBytes(ByteArray(2048) { it.toByte() })
        }

        val book = Book(
            id = "local-book-1",
            title = "Local Book",
            format = BookFormat.EPUB,
            localPath = bookFile.absolutePath,
            addedAt = Instant.now()
        )

        coEvery { bookDao.insert(any()) } returns Unit
        coEvery { bookDao.updateFileHash(any(), any()) } returns Unit
        coEvery { metadataExtractor.extractMetadata(any()) } returns BookMetadata(
            title = "Local Book", author = null, coverUrl = null,
        )

        repository.addLocalBook(book)

        coVerify { bookDao.insert(any()) }
    }
}
