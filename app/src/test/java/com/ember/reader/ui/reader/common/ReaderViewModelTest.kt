package com.ember.reader.ui.reader.common

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.SyncFrequency
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.core.repository.ReaderPreferencesRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class ReaderViewModelTest {

    @MockK
    private lateinit var bookRepository: BookRepository

    @MockK
    private lateinit var bookOpener: BookOpener

    @MockK
    private lateinit var readingProgressRepository: ReadingProgressRepository

    @MockK
    private lateinit var bookmarkRepository: BookmarkRepository

    @MockK
    private lateinit var highlightRepository: HighlightRepository

    @MockK
    private lateinit var readerPreferencesRepository: ReaderPreferencesRepository

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var syncPreferencesRepository: SyncPreferencesRepository

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { readerPreferencesRepository.preferencesFlow } returns flowOf(ReaderPreferences())
        every { bookmarkRepository.observeByBookId(any()) } returns flowOf(emptyList())
        every { highlightRepository.observeByBookId(any()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(bookId: String = "test-book-1"): ReaderViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("bookId" to bookId))
        return ReaderViewModel(
            savedStateHandle = savedStateHandle,
            bookRepository = bookRepository,
            bookOpener = bookOpener,
            readingProgressRepository = readingProgressRepository,
            bookmarkRepository = bookmarkRepository,
            highlightRepository = highlightRepository,
            readerPreferencesRepository = readerPreferencesRepository,
            serverRepository = serverRepository,
            syncPreferencesRepository = syncPreferencesRepository,
        )
    }

    @Test
    fun `loadBook with null book sets Error state`() = runTest {
        coEvery { bookRepository.getById("missing-book") } returns null

        val viewModel = createViewModel(bookId = "missing-book")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("Book not found", (state as ReaderUiState.Error).message)
    }

    @Test
    fun `toggleChrome flips boolean`() = runTest {
        coEvery { bookRepository.getById(any()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.chromeVisible.value)

        viewModel.toggleChrome()
        assertFalse(viewModel.chromeVisible.value)

        viewModel.toggleChrome()
        assertTrue(viewModel.chromeVisible.value)
    }

    @Test
    fun `dismissSyncConflict clears state`() = runTest {
        coEvery { bookRepository.getById(any()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially null
        assertNull(viewModel.syncConflict.value)

        // Dismiss should keep it null (no-op if already null, but verifies the method works)
        viewModel.dismissSyncConflict()
        assertNull(viewModel.syncConflict.value)
    }

    @Test
    fun `addBookmark delegates to BookmarkRepository`() = runTest {
        coEvery { bookRepository.getById(any()) } returns null
        coEvery { bookmarkRepository.addBookmark(any(), any(), any()) } returns 1L

        val viewModel = createViewModel(bookId = "book-123")
        advanceUntilIdle()

        // Set a current locator; addBookmark returns early if currentLocator is null
        val testLocator = mockk<Locator>(relaxed = true)
        every { testLocator.title } returns "Chapter 1"
        every { testLocator.toJSON() } returns org.json.JSONObject(
            mapOf("href" to "/chapter1.xhtml", "type" to "application/xhtml+xml"),
        )
        every { testLocator.locations } returns mockk(relaxed = true) {
            every { totalProgression } returns 0.1
        }

        // Mock the progress save that happens via scheduleSaveProgress
        coEvery {
            readingProgressRepository.updateProgress(any(), any(), any(), any())
        } returns Unit

        viewModel.onLocatorChanged(testLocator)
        viewModel.addBookmark()
        advanceUntilIdle()

        coVerify {
            bookmarkRepository.addBookmark(
                bookId = "book-123",
                locatorJson = any(),
                title = "Chapter 1",
            )
        }
    }
}
