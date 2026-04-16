package com.ember.reader.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.ember.reader.core.database.query.LibrarySortOrder
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.SyncStatus
import com.ember.reader.core.sync.SyncStatusProber
import com.ember.reader.core.sync.SyncStatusRepository
import com.ember.reader.ui.organize.OrganizeFilesViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class LibraryViewModelTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var bookRepository: BookRepository

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var readingProgressRepository: ReadingProgressRepository

    @MockK
    private lateinit var grimmoryClient: GrimmoryClient

    @MockK
    private lateinit var grimmoryAppClient: GrimmoryAppClient

    @MockK(relaxed = true)
    private lateinit var organizeFilesViewModelFactory: OrganizeFilesViewModel.Factory

    @MockK(relaxed = true)
    private lateinit var syncStatusProber: SyncStatusProber

    private val testDispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-04-14T12:00:00Z"), ZoneOffset.UTC)
    private val syncStatusRepository =
        SyncStatusRepository(com.ember.reader.core.testutil.FakeSyncStatusDao(), clock)

    private val testServer = Server(
        id = 1L,
        name = "Test",
        url = "http://localhost/api/v1/opds",
        opdsUsername = "user",
        opdsPassword = "pass",
        kosyncUsername = "kuser",
        kosyncPassword = "kpass"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "dXNlcjpwYXNz"
        every { bookRepository.observeByServer(1L) } returns flowOf(emptyList())
        coEvery { serverRepository.getById(1L) } returns testServer
        coEvery { bookRepository.refreshFromServer(any(), any(), any()) } returns Result.success(
            OpdsBookPage(books = emptyList())
        )
        coEvery { bookRepository.reconcileOpdsLibrary(any(), any()) } returns Result.success(0)
        coEvery {
            grimmoryAppClient.getFilterOptions(
                baseUrl = any(),
                serverId = any(),
                libraryId = any(),
                shelfId = any(),
                magicShelfId = any()
            )
        } returns Result.failure(RuntimeException("unused in test"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(path: String = "/api/v1/opds/catalog"): LibraryViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf("serverId" to 1L, "path" to path)
        )
        return LibraryViewModel(
            savedStateHandle,
            context,
            bookRepository,
            serverRepository,
            readingProgressRepository,
            grimmoryClient,
            grimmoryAppClient,
            syncStatusRepository,
            syncStatusProber,
            organizeFilesViewModelFactory
        )
    }

    @Test
    fun `toggleViewMode alternates GRID and LIST`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewMode.GRID, viewModel.viewMode.value)

        viewModel.toggleViewMode()
        assertEquals(ViewMode.LIST, viewModel.viewMode.value)

        viewModel.toggleViewMode()
        assertEquals(ViewMode.GRID, viewModel.viewMode.value)
    }

    @Test
    fun `updateSortOrder changes sort`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(LibrarySortOrder.TITLE, viewModel.sortOrder.value)

        viewModel.updateSortOrder(LibrarySortOrder.AUTHOR)
        assertEquals(LibrarySortOrder.AUTHOR, viewModel.sortOrder.value)

        viewModel.updateSortOrder(LibrarySortOrder.RECENT)
        assertEquals(LibrarySortOrder.RECENT, viewModel.sortOrder.value)
    }

    @Test
    fun `toggleDownloadedOnly flips boolean`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.downloadedOnly.value)

        viewModel.toggleDownloadedOnly()
        assertTrue(viewModel.downloadedOnly.value)

        viewModel.toggleDownloadedOnly()
        assertFalse(viewModel.downloadedOnly.value)
    }

    @Test
    fun `syncStatus reflects the repository's state for this server`() = runTest {
        val viewModel = createViewModel()
        // WhileSubscribed-based stateIn only runs while someone is collecting, so
        // keep a live subscriber for the duration of the test.
        val collector = launch { viewModel.syncStatus.collect { } }
        advanceUntilIdle()

        assertEquals(SyncStatus.Unknown, viewModel.syncStatus.value)

        syncStatusRepository.reportFailure(1L, GrimmoryAuthExpiredException(1L))
        advanceUntilIdle()

        assertTrue(viewModel.syncStatus.value is SyncStatus.AuthExpired)
        collector.cancel()
    }

    @Test
    fun `resolveSelectedGrimmoryIds filters out books without grimmoryBookId`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("grim-1")
        viewModel.toggleSelection("local-only")

        coEvery { bookRepository.getByIds(setOf("grim-1", "local-only")) } returns listOf(
            Book(
                id = "grim-1",
                title = "G",
                format = BookFormat.EPUB,
                opdsEntryId = "urn:booklore:book:42"
            ),
            Book(id = "local-only", title = "L", format = BookFormat.EPUB)
        )

        val ids = viewModel.resolveSelectedGrimmoryIds()

        assertEquals(listOf(42L), ids)
    }

    @Test
    fun `resolveSelectedGrimmoryIds short-circuits on empty selection`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val ids = viewModel.resolveSelectedGrimmoryIds()

        assertTrue(ids.isEmpty())
    }

    @Test
    fun `syncStatus ignores events for other servers`() = runTest {
        val viewModel = createViewModel()
        val collector = launch { viewModel.syncStatus.collect { } }
        advanceUntilIdle()

        syncStatusRepository.reportFailure(999L, GrimmoryAuthExpiredException(999L))
        advanceUntilIdle()

        assertEquals(SyncStatus.Unknown, viewModel.syncStatus.value)
        collector.cancel()
    }

    @Test
    fun `Recently Added path defaults sort order to RECENT`() = runTest {
        val viewModel = createViewModel(path = "grimmory:recentlyAdded")
        advanceUntilIdle()

        assertEquals(LibrarySortOrder.RECENT, viewModel.sortOrder.value)
    }

    @Test
    fun `seriesName path defaults sort order to SERIES`() = runTest {
        val viewModel = createViewModel(path = "grimmory:seriesName=Foundation")
        advanceUntilIdle()

        assertEquals(LibrarySortOrder.SERIES, viewModel.sortOrder.value)
    }

    /**
     * Regression guard: Grimmory shelf/library paths must prime sessionIds with `emptySet()` so
     * the DAO query gates rows to books the mediator has fetched for this shelf. A `null` seed
     * skips the `id IN (...)` clause entirely and the UI shows every book on the server — the
     * exact bug this encodes. Adding a new Grimmory scoped key to `GRIMMORY_FILTER_KEYS` is
     * incomplete without an entry here.
     */
    @Test
    fun `grimmory shelf path seeds sessionIds with emptySet, unscoped root seeds with null`() =
        runTest {
            val sessionIdsSlot = slot<MutableStateFlow<Set<String>?>>()
            every {
                bookRepository.pageByServer(
                    server = any(),
                    catalogPath = any(),
                    sort = any(),
                    formatFilter = any(),
                    downloadedOnly = any(),
                    query = any(),
                    grimmoryFilter = any(),
                    sessionIds = capture(sessionIdsSlot),
                    pageSize = any()
                )
            } returns flowOf(PagingData.empty<Book>())
            coEvery { bookRepository.reconcileGrimmoryLibrary(any()) } returns Result.success(0)

            val shelfVm = createViewModel(path = "grimmory:shelfId=1")
            val shelfCollector = launch { shelfVm.bookPagingData.collect { } }
            advanceUntilIdle()

            assertTrue(sessionIdsSlot.isCaptured, "pageByServer was not invoked for shelf path")
            assertNotNull(sessionIdsSlot.captured.value, "shelf must seed sessionIds non-null")
            assertEquals(emptySet<String>(), sessionIdsSlot.captured.value)
            shelfCollector.cancel()

            val rootVm = createViewModel(path = "grimmory:all")
            val rootCollector = launch { rootVm.bookPagingData.collect { } }
            advanceUntilIdle()

            assertNull(sessionIdsSlot.captured.value, "unscoped root must seed sessionIds null")
            rootCollector.cancel()
        }
}
