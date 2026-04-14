package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.CreateBookmarkRequest
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateBookmarkRequest
import com.ember.reader.core.testutil.FakeBookmarkDao
import com.ember.reader.core.testutil.TestFixtures.bookmarkEntity
import com.ember.reader.core.testutil.TestFixtures.grimmoryBookmark
import com.ember.reader.core.testutil.TestFixtures.server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BookmarkSyncManagerTest {

    private val grimmoryClient: GrimmoryClient = mockk(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-04-14T12:00:00Z"), ZoneOffset.UTC)
    private val syncStatusRepository = SyncStatusRepository(clock)
    private lateinit var dao: FakeBookmarkDao
    private lateinit var syncManager: BookmarkSyncManager

    private val testServer = server()
    private val bookId = "book-1"
    private val grimmoryBookId = 101L

    @BeforeEach
    fun setUp() {
        dao = FakeBookmarkDao()
        syncManager = BookmarkSyncManager(dao, grimmoryClient, syncStatusRepository)
    }

    @Nested
    inner class ServerToLocal {

        @Test
        fun `new server bookmark creates local entity with correct locatorJson`() = runTest {
            val serverBm = grimmoryBookmark(id = 10, cfi = "/6/4!/4/2", title = "Ch 1")
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            val locals = dao.all.filter { it.deletedAt == null }
            assertEquals(1, locals.size)
            val created = locals.first()
            assertEquals(bookId, created.bookId)
            assertEquals(10L, created.remoteId)
            assertEquals("Ch 1", created.title)
            // Verify the locator JSON contains the CFI via real CfiLocatorConverter
            val extractedCfi = CfiLocatorConverter.extractCfi(created.locatorJson)
            assertEquals("/6/4!/4/2", extractedCfi)
        }

        @Test
        fun `server bookmark with null cfi is skipped`() = runTest {
            val serverBm = grimmoryBookmark(id = 10, cfi = null)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            assertTrue(dao.all.isEmpty())
        }

        @Test
        fun `server newer than local updates local title and timestamp`() = runTest {
            val localTime = Instant.parse("2026-01-01T10:00:00Z")
            val serverTime = "2026-01-01T12:00:00Z"

            // Pre-populate local bookmark linked to remote
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, title = "Old Title", updatedAt = localTime))

            val serverBm = grimmoryBookmark(id = 10, title = "New Title", updatedAt = serverTime)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            val updated = dao.all.first()
            assertEquals("New Title", updated.title)
            assertEquals(Instant.parse(serverTime), updated.updatedAt)
        }

        @Test
        fun `local newer than server pushes update to server`() = runTest {
            val localTime = Instant.parse("2026-01-01T14:00:00Z")
            val serverTime = "2026-01-01T10:00:00Z"

            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, title = "Local Title", updatedAt = localTime))

            val serverBm = grimmoryBookmark(id = 10, title = "Server Title", updatedAt = serverTime)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))
            coEvery { grimmoryClient.updateBookmark(any(), any(), any(), any()) } returns Result.success(serverBm)

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            val requestSlot = slot<UpdateBookmarkRequest>()
            coVerify { grimmoryClient.updateBookmark(any(), any(), eq(10L), capture(requestSlot)) }
            assertEquals("Local Title", requestSlot.captured.title)
        }

        @Test
        fun `equal timestamps makes no changes`() = runTest {
            val time = Instant.parse("2026-01-01T12:00:00Z")

            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, title = "Same Title", updatedAt = time))

            val serverBm = grimmoryBookmark(id = 10, title = "Same Title", updatedAt = "2026-01-01T12:00:00Z")
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // Neither update should happen
            coVerify(exactly = 0) { grimmoryClient.updateBookmark(any(), any(), any(), any()) }
            // Local title unchanged
            assertEquals("Same Title", dao.all.first().title)
        }

        @Test
        fun `null server timestamp treated as not-newer`() = runTest {
            val localTime = Instant.parse("2026-01-01T12:00:00Z")
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, title = "Local", updatedAt = localTime))

            val serverBm = grimmoryBookmark(id = 10, title = "Server", updatedAt = null)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // Local not overwritten because server timestamp is null
            assertEquals("Local", dao.all.first().title)
            assertEquals(localTime, dao.all.first().updatedAt)
        }
    }

    @Nested
    inner class TombstoneHandling {

        @Test
        fun `tombstoned local with remoteId deletes from server`() = runTest {
            dao.insert(
                bookmarkEntity(
                    bookId = bookId,
                    remoteId = 10,
                    deletedAt = Instant.parse("2026-01-02T00:00:00Z"),
                )
            )

            val serverBm = grimmoryBookmark(id = 10)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))
            coEvery { grimmoryClient.deleteBookmark(any(), any(), any()) } returns Result.success(Unit)

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            coVerify { grimmoryClient.deleteBookmark(any(), any(), eq(10L)) }
        }

        @Test
        fun `tombstoned never-synced bookmark is hard-deleted locally`() = runTest {
            dao.insert(
                bookmarkEntity(
                    bookId = bookId,
                    remoteId = null,
                    deletedAt = Instant.parse("2026-01-02T00:00:00Z"),
                )
            )

            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(emptyList())

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // After sync + cleanup, no bookmarks should remain
            assertTrue(dao.all.isEmpty())
        }

        @Test
        fun `cleanupTombstones removes remaining tombstones after sync`() = runTest {
            // Tombstoned bookmark that was matched and server-deleted in the server loop
            dao.insert(
                bookmarkEntity(
                    bookId = bookId,
                    remoteId = 10,
                    deletedAt = Instant.parse("2026-01-02T00:00:00Z"),
                )
            )

            val serverBm = grimmoryBookmark(id = 10)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))
            coEvery { grimmoryClient.deleteBookmark(any(), any(), any()) } returns Result.success(Unit)

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // After cleanupTombstones, no tombstoned items should remain
            assertTrue(dao.all.none { it.deletedAt != null })
        }
    }

    @Nested
    inner class LocalToServer {

        @Test
        fun `new local bookmark pushes to server and stores remoteId`() = runTest {
            val locatorJson = CfiLocatorConverter.buildLocatorJson("/6/4!/4/2", chapterTitle = "Ch 1")
            dao.insert(bookmarkEntity(bookId = bookId, locatorJson = locatorJson, title = "Ch 1", remoteId = null))

            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { grimmoryClient.createBookmark(any(), any(), any()) } returns Result.success(
                grimmoryBookmark(id = 99, cfi = "/6/4!/4/2", title = "Ch 1")
            )

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            val local = dao.all.first()
            assertEquals(99L, local.remoteId)

            val requestSlot = slot<CreateBookmarkRequest>()
            coVerify { grimmoryClient.createBookmark(any(), any(), capture(requestSlot)) }
            assertEquals(grimmoryBookId, requestSlot.captured.bookId)
            assertEquals("/6/4!/4/2", requestSlot.captured.cfi)
        }

        @Test
        fun `new local matching existing server CFI links instead of creating`() = runTest {
            val cfi = "/6/4!/4/2"
            val locatorJson = CfiLocatorConverter.buildLocatorJson(cfi)
            dao.insert(bookmarkEntity(bookId = bookId, locatorJson = locatorJson, remoteId = null))

            val serverBm = grimmoryBookmark(id = 50, cfi = cfi)
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(listOf(serverBm))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // Should link, not create
            coVerify(exactly = 0) { grimmoryClient.createBookmark(any(), any(), any()) }
            assertEquals(50L, dao.all.first().remoteId)
        }

        @Test
        fun `409 conflict recovery re-fetches and links by CFI`() = runTest {
            val cfi = "/6/8!/4/2"
            val locatorJson = CfiLocatorConverter.buildLocatorJson(cfi)
            dao.insert(bookmarkEntity(bookId = bookId, locatorJson = locatorJson, remoteId = null))

            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returnsMany listOf(
                Result.success(emptyList()), // initial fetch returns nothing
                Result.success(listOf(grimmoryBookmark(id = 77, cfi = cfi))) // refetch after 409
            )
            coEvery { grimmoryClient.createBookmark(any(), any(), any()) } returns
                Result.failure(Exception("409 Conflict"))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            assertEquals(77L, dao.all.first().remoteId)
        }

        @Test
        fun `local with remoteId missing from server is deleted`() = runTest {
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 42))

            // Server returns bookmarks that don't include id=42
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(
                listOf(grimmoryBookmark(id = 99, cfi = "/6/10"))
            )

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // The bookmark with remoteId=42 should be deleted (server removed it)
            assertTrue(dao.all.none { it.remoteId == 42L })
        }
    }

    @Nested
    inner class FullSync {

        @Test
        fun `full sync with mixed adds, updates, and deletes`() = runTest {
            // Local state:
            // 1. Linked to remote 10, local is newer → should push update
            // 2. No remoteId, not deleted → new local, should push
            // 3. Tombstoned, remoteId=20 → should delete from server
            // 4. Linked to remote 30, no longer on server → should be deleted locally

            val locatorWithCfi = CfiLocatorConverter.buildLocatorJson("/6/100")

            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, title = "Updated locally", updatedAt = Instant.parse("2026-01-02T00:00:00Z")))
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = null, locatorJson = locatorWithCfi, title = "Brand new"))
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 20, deletedAt = Instant.parse("2026-01-01T06:00:00Z")))
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 30, title = "Server deleted this"))

            // Server state:
            // remote 10 exists with older timestamp
            // remote 20 exists (will be deleted by tombstone)
            // remote 30 is GONE (server deleted it)
            // remote 50 is NEW from server
            val serverBookmarks = listOf(
                grimmoryBookmark(id = 10, cfi = "/6/4", title = "Old server title", updatedAt = "2026-01-01T00:00:00Z"),
                grimmoryBookmark(id = 20, cfi = "/6/8", title = "Will be deleted"),
                grimmoryBookmark(id = 50, cfi = "/6/50", title = "New from server", createdAt = "2026-01-01T10:00:00Z", updatedAt = "2026-01-01T10:00:00Z"),
            )

            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(serverBookmarks)
            coEvery { grimmoryClient.deleteBookmark(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.updateBookmark(any(), any(), any(), any()) } returns Result.success(grimmoryBookmark(id = 10))
            coEvery { grimmoryClient.createBookmark(any(), any(), any()) } returns Result.success(grimmoryBookmark(id = 60, cfi = "/6/100"))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            val finalState = dao.all

            // remote 10: still present, pushed update to server
            val bm10 = finalState.find { it.remoteId == 10L }
            assertNotNull(bm10)
            assertEquals("Updated locally", bm10!!.title)

            // Brand new local: now has remoteId=60
            val newLocal = finalState.find { it.remoteId == 60L }
            assertNotNull(newLocal)

            // remote 20: tombstone cleaned up
            assertNull(finalState.find { it.remoteId == 20L })

            // remote 30: deleted because server removed it
            assertNull(finalState.find { it.remoteId == 30L })

            // remote 50: new from server, created locally
            val bm50 = finalState.find { it.remoteId == 50L }
            assertNotNull(bm50)
            assertEquals("New from server", bm50!!.title)

            // Verify API calls
            coVerify { grimmoryClient.deleteBookmark(any(), any(), eq(20L)) }
            coVerify { grimmoryClient.updateBookmark(any(), any(), eq(10L), any()) }
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `fetch failure aborts sync without modifying local state`() = runTest {
            dao.insert(bookmarkEntity(bookId = bookId, remoteId = 10, title = "Existing"))

            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns
                Result.failure(Exception("Network error"))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            // Local state unchanged
            assertEquals(1, dao.all.size)
            assertEquals("Existing", dao.all.first().title)
        }
    }

    @Nested
    inner class StatusReporting {

        @Test
        fun `successful sync reports Ok`() = runTest {
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns Result.success(emptyList())

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.Ok)
        }

        @Test
        fun `fetch failure reports the classified error`() = runTest {
            coEvery { grimmoryClient.getBookmarks(any(), any(), any()) } returns
                Result.failure(java.net.UnknownHostException("grimmory.invalid"))

            syncManager.syncBookmarksForBook(testServer, bookId, grimmoryBookId)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.NetworkError)
        }
    }
}
