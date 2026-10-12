package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateBookmarkRequest
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.testutil.FakeBookmarkDao
import com.ember.reader.core.testutil.TestFixtures.bookmarkEntity
import com.ember.reader.core.testutil.TestFixtures.grimmoryBookmark
import com.ember.reader.core.testutil.TestFixtures.server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookmarkRepairerTest {

    private val grimmoryClient: GrimmoryClient = mockk(relaxed = true)
    private lateinit var dao: FakeBookmarkDao
    private lateinit var repairer: BookmarkRepairer

    private val testServer = server()
    private val bookId = "book-1"
    private val grimmoryBookId = 101L

    @BeforeEach
    fun setUp() {
        dao = FakeBookmarkDao()
        repairer = BookmarkRepairer(dao, grimmoryClient)
        coEvery { grimmoryClient.updateBookmark(any(), any(), any(), any()) } returns
            Result.success(grimmoryBookmark())
    }

    /** Places every bookmark it is asked about at [REAL_CFI], and records what it was asked. */
    private val asked = mutableListOf<Bookmark>()
    private val placeAll: suspend (List<Bookmark>) -> Map<Long, String> = { bookmarks ->
        asked += bookmarks
        bookmarks.associate { it.id to REAL_CFI }
    }

    private fun serverHas(vararg bookmarks: Pair<Long, String?>) {
        coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns
            Result.success(bookmarks.map { (id, cfi) -> grimmoryBookmark(id = id, cfi = cfi) })
    }

    private suspend fun repair(compute: suspend (List<Bookmark>) -> Map<Long, String> = placeAll) =
        repairer.repair(testServer, bookId, grimmoryBookId, compute).getOrThrow()

    @Test
    fun `a bookmark pushed with a fake CFI gets a real one on the server and locally`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))
        serverHas(10L to "0.4231")

        val summary = repair()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        coVerify(exactly = 1) {
            grimmoryClient.updateBookmark(any(), any(), 10, UpdateBookmarkRequest(cfi = REAL_CFI))
        }
        assertEquals(REAL_CFI, dao.all.single().cfi)
    }

    @Test
    fun `a bookmark that never synced only has its CFI stored`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = null))
        serverHas()

        val summary = repair()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        coVerify(exactly = 0) { grimmoryClient.updateBookmark(any(), any(), any(), any()) }
        assertEquals(REAL_CFI, dao.all.single().cfi)
    }

    @Test
    fun `a bookmark whose server copy is gone only has its CFI stored`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))
        serverHas()

        repair()

        coVerify(exactly = 0) { grimmoryClient.updateBookmark(any(), any(), any(), any()) }
        assertEquals(REAL_CFI, dao.all.single().cfi)
    }

    @Test
    fun `a real CFI already on the server is taken without working one out`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))
        serverHas(10L to OTHER_REAL_CFI)

        val summary = repair()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        assertTrue(asked.isEmpty())
        coVerify(exactly = 0) { grimmoryClient.updateBookmark(any(), any(), any(), any()) }
        assertEquals(OTHER_REAL_CFI, dao.all.single().cfi)
    }

    @Test
    fun `a bookmark that has not been placed in the book gets the real CFI in its locator too`() = runTest {
        dao.insert(
            bookmarkEntity(
                bookId = bookId,
                remoteId = 10,
                locatorJson = CfiLocatorConverter.buildLocatorJson(cfi = "0.4231", chapterTitle = "Ch 2"),
            )
        )
        serverHas(10L to OTHER_REAL_CFI)

        repair()

        val stored = dao.all.single()
        assertEquals(OTHER_REAL_CFI, CfiLocatorConverter.unplacedCfi(stored.locatorJson))
        assertEquals("Ch 2", CfiLocatorConverter.extractTitle(stored.locatorJson))
    }

    @Test
    fun `a bookmark that cannot be placed is left as it is`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))
        serverHas(10L to "0.4231")

        val summary = repair { emptyMap() }

        assertEquals(RepairSummary(repaired = 0, unresolved = 1), summary)
        coVerify(exactly = 0) { grimmoryClient.updateBookmark(any(), any(), any(), any()) }
        assertNull(dao.all.single().cfi)
    }

    @Test
    fun `a bookmark the server refuses to update keeps no CFI, so it is offered again`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))
        serverHas(10L to "0.4231")
        coEvery { grimmoryClient.updateBookmark(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("500"))

        val summary = repair()

        assertEquals(RepairSummary(repaired = 0, unresolved = 1), summary)
        assertNull(dao.all.single().cfi)
    }

    @Test
    fun `bookmarks with a real CFI and deleted ones are not touched`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, cfi = OTHER_REAL_CFI))
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 11, deletedAt = java.time.Instant.now()))

        val summary = repair()

        assertEquals(RepairSummary(repaired = 0, unresolved = 0), summary)
        coVerify(exactly = 0) { grimmoryClient.getBookmarks(any(), any(), any()) }
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a book that does not sync gets CFIs stored without calling the server`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))

        val summary = repairer.repair(server = null, bookId = bookId, grimmoryBookId = null, placeAll).getOrThrow()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        coVerify(exactly = 0) { grimmoryClient.getBookmarks(any(), any(), any()) }
        coVerify(exactly = 0) { grimmoryClient.updateBookmark(any(), any(), any(), any()) }
        assertEquals(REAL_CFI, dao.all.single().cfi)
    }

    @Test
    fun `a server that cannot be reached fails the repair without changing anything`() = runTest {
        dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))

        val result = repairer.repair(testServer, bookId, grimmoryBookId, placeAll)

        assertTrue(result.isFailure)
        assertNull(dao.all.single().cfi)
    }

    private companion object {
        const val REAL_CFI = "epubcfi(/6/8!/4/2/2/1:0)"
        const val OTHER_REAL_CFI = "epubcfi(/6/10!/4/6/1:12)"
    }
}
