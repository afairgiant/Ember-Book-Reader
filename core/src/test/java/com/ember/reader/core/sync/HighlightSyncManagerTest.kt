package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.CreateAnnotationRequest
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateAnnotationRequest
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.testutil.FakeHighlightDao
import com.ember.reader.core.testutil.FakeSyncStatusDao
import com.ember.reader.core.testutil.TestFixtures.grimmoryAnnotation
import com.ember.reader.core.testutil.TestFixtures.highlightEntity
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class HighlightSyncManagerTest {

    private val grimmoryClient: GrimmoryClient = mockk(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-04-14T12:00:00Z"), ZoneOffset.UTC)
    private val syncStatusRepository = SyncStatusRepository(FakeSyncStatusDao(), clock)
    private lateinit var dao: FakeHighlightDao
    private lateinit var syncManager: HighlightSyncManager

    private val testServer = server()
    private val bookId = "book-1"
    private val grimmoryBookId = 101L

    @BeforeEach
    fun setUp() {
        dao = FakeHighlightDao()
        syncManager = HighlightSyncManager(dao, grimmoryClient, syncStatusRepository)
    }

    @Nested
    inner class ServerToLocal {

        @Test
        fun `new server annotation creates local highlight with correct color and locator`() =
            runTest {
                val serverAnn = grimmoryAnnotation(
                    id = 10, cfi = "/6/4!/4/2", text = "some text", color = "4ADE80",
                    note = "a note", chapterTitle = "Ch 1"
                )
                coEvery {
                    grimmoryClient.getAnnotations(any(), any(), any())
                } returns Result.success(listOf(serverAnn))

                syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

                val locals = dao.all
                assertEquals(1, locals.size)
                val created = locals.first()
                assertEquals(bookId, created.bookId)
                assertEquals(10L, created.remoteId)
                assertEquals(HighlightColor.GREEN, created.color)
                assertEquals("some text", created.selectedText)
                assertEquals("a note", created.annotation)
                // Verify CFI round-trips through real CfiLocatorConverter
                assertEquals("/6/4!/4/2", CfiLocatorConverter.extractCfi(created.locatorJson))
            }

        @Test
        fun `server annotation with null cfi is skipped`() = runTest {
            val serverAnn = grimmoryAnnotation(id = 10, cfi = null)
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertTrue(dao.all.isEmpty())
        }

        @Test
        fun `server newer than local updates annotation text and selectedText`() = runTest {
            val localTime = Instant.parse("2026-01-01T10:00:00Z")
            val serverTime = "2026-01-01T14:00:00Z"

            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = 10,
                    annotation = "old note", selectedText = "old text",
                    updatedAt = localTime
                )
            )

            val serverAnn = grimmoryAnnotation(
                id = 10, text = "new text", note = "new note",
                color = "FACC15", updatedAt = serverTime
            )
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            val updated = dao.all.first()
            assertEquals("new note", updated.annotation)
            assertEquals("new text", updated.selectedText)
            assertEquals(Instant.parse(serverTime), updated.updatedAt)
        }

        @Test
        fun `local newer than server pushes update to server`() = runTest {
            val localTime = Instant.parse("2026-01-01T14:00:00Z")
            val serverTime = "2026-01-01T10:00:00Z"

            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = 10,
                    color = HighlightColor.BLUE, annotation = "local note",
                    updatedAt = localTime
                )
            )

            val serverAnn = grimmoryAnnotation(
                id = 10, color = "38BDF8", note = "server note",
                updatedAt = serverTime
            )
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))
            coEvery {
                grimmoryClient.updateAnnotation(any(), any(), any(), any())
            } returns Result.success(serverAnn)

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            val requestSlot = slot<UpdateAnnotationRequest>()
            coVerify { grimmoryClient.updateAnnotation(any(), any(), eq(10L), capture(requestSlot)) }
            assertEquals(HighlightColor.BLUE.hex, requestSlot.captured.color)
            assertEquals("local note", requestSlot.captured.note)
        }
    }

    @Nested
    inner class ColorRefresh {

        @Test
        fun `color always refreshed from server regardless of timestamp winner`() = runTest {
            val time = Instant.parse("2026-01-01T12:00:00Z")

            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = 10,
                    color = HighlightColor.YELLOW, updatedAt = time
                )
            )

            // Same timestamp (no winner), but server has different color
            val serverAnn = grimmoryAnnotation(
                id = 10, color = "F472B6", updatedAt = "2026-01-01T12:00:00Z"
            )
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertEquals(HighlightColor.PINK, dao.all.first().color)
        }
    }

    @Nested
    inner class LocatorFixing {

        @Test
        fun `empty href locator gets rebuilt from server CFI`() = runTest {
            val malformedLocator = """{"href":"","type":"application/xhtml+xml","locations":{"progression":"0.5"}}"""

            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = 10,
                    locatorJson = malformedLocator, selectedText = "original text",
                    updatedAt = Instant.parse("2026-01-01T12:00:00Z")
                )
            )

            val serverAnn = grimmoryAnnotation(
                id = 10, cfi = "/6/4!/4/2", text = "server text",
                chapterTitle = "Fixed Chapter", color = "FACC15",
                updatedAt = "2026-01-01T12:00:00Z"
            )
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            val updated = dao.all.first()
            // Locator should now contain the server CFI
            val extractedCfi = CfiLocatorConverter.extractCfi(updated.locatorJson)
            assertEquals("/6/4!/4/2", extractedCfi)
        }

        @Test
        fun `non-empty href locator is NOT rebuilt`() = runTest {
            val goodLocator = """{"href":"/chapter1.xhtml","type":"application/xhtml+xml","locations":{"fragments":["epubcfi(/6/2)"]}}"""

            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = 10,
                    locatorJson = goodLocator,
                    updatedAt = Instant.parse("2026-01-01T12:00:00Z")
                )
            )

            val serverAnn = grimmoryAnnotation(
                id = 10, cfi = "/6/99!/4/2", color = "FACC15",
                updatedAt = "2026-01-01T12:00:00Z"
            )
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            val updated = dao.all.first()
            // Locator should NOT be replaced — it has a real href
            assertTrue(updated.locatorJson.contains("/chapter1.xhtml"))
        }
    }

    @Nested
    inner class LocalToServer {

        @Test
        fun `new local highlight pushes to server and stores remoteId`() = runTest {
            val locatorJson = CfiLocatorConverter.buildLocatorJson("/6/8", selectedText = "my text", chapterTitle = "Ch 2")
            dao.insert(
                highlightEntity(
                    bookId = bookId, locatorJson = locatorJson,
                    color = HighlightColor.BLUE, annotation = "note", selectedText = "my text",
                    remoteId = null
                )
            )

            coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(emptyList())
            coEvery { grimmoryClient.createAnnotation(any(), any(), any()) } returns Result.success(
                grimmoryAnnotation(id = 88, cfi = "/6/8")
            )

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertEquals(88L, dao.all.first().remoteId)

            val requestSlot = slot<CreateAnnotationRequest>()
            coVerify { grimmoryClient.createAnnotation(any(), any(), capture(requestSlot)) }
            assertEquals(grimmoryBookId, requestSlot.captured.bookId)
            assertEquals("/6/8", requestSlot.captured.cfi)
            assertEquals("my text", requestSlot.captured.text)
            assertEquals(HighlightColor.BLUE.hex, requestSlot.captured.color)
            assertEquals("note", requestSlot.captured.note)
            assertEquals("Ch 2", requestSlot.captured.chapterTitle)
        }

        @Test
        fun `new local matching existing server CFI links instead of creating`() = runTest {
            val cfi = "/6/4"
            val locatorJson = CfiLocatorConverter.buildLocatorJson(cfi)
            dao.insert(highlightEntity(bookId = bookId, locatorJson = locatorJson, remoteId = null))

            val serverAnn = grimmoryAnnotation(id = 50, cfi = cfi)
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            coVerify(exactly = 0) { grimmoryClient.createAnnotation(any(), any(), any()) }
            assertEquals(50L, dao.all.first().remoteId)
        }

        @Test
        fun `local with remoteId missing from server is deleted`() = runTest {
            dao.insert(highlightEntity(bookId = bookId, remoteId = 42))

            coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(emptyList())

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertTrue(dao.all.none { it.remoteId == 42L })
        }
    }

    @Nested
    inner class TombstoneHandling {

        @Test
        fun `tombstoned local with remoteId deletes from server`() = runTest {
            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = 10,
                    deletedAt = Instant.parse("2026-01-02T00:00:00Z")
                )
            )

            val serverAnn = grimmoryAnnotation(id = 10)
            coEvery {
                grimmoryClient.getAnnotations(any(), any(), any())
            } returns Result.success(listOf(serverAnn))
            coEvery { grimmoryClient.deleteAnnotation(any(), any(), any()) } returns Result.success(Unit)

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            coVerify { grimmoryClient.deleteAnnotation(any(), any(), eq(10L)) }
        }

        @Test
        fun `tombstoned never-synced highlight is hard-deleted locally`() = runTest {
            dao.insert(
                highlightEntity(
                    bookId = bookId, remoteId = null,
                    deletedAt = Instant.parse("2026-01-02T00:00:00Z")
                )
            )

            coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(emptyList())

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertTrue(dao.all.isEmpty())
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `fetch failure aborts sync without modifying local state`() = runTest {
            dao.insert(highlightEntity(bookId = bookId, remoteId = 10, annotation = "keep this"))

            coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns
                Result.failure(Exception("Network error"))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertEquals(1, dao.all.size)
            assertEquals("keep this", dao.all.first().annotation)
        }
    }

    @Nested
    inner class StatusReporting {

        @Test
        fun `successful sync reports Ok`() = runTest {
            coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(emptyList())

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.Ok)
        }

        @Test
        fun `fetch failure reports the classified error`() = runTest {
            coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns
                Result.failure(java.net.UnknownHostException("grimmory.invalid"))

            syncManager.syncHighlightsForBook(testServer, bookId, grimmoryBookId)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.NetworkError)
        }
    }
}
