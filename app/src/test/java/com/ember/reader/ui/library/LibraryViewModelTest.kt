package com.ember.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class LibraryViewModelTest {

    @MockK
    private lateinit var bookRepository: BookRepository

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var readingProgressRepository: ReadingProgressRepository

    @MockK
    private lateinit var grimmoryClient: GrimmoryClient

    @MockK
    private lateinit var grimmoryTokenManager: GrimmoryTokenManager

    private val testDispatcher = StandardTestDispatcher()

    private val testServer = Server(
        id = 1L,
        name = "Test",
        url = "http://localhost/api/v1/opds",
        opdsUsername = "user",
        opdsPassword = "pass",
        kosyncUsername = "kuser",
        kosyncPassword = "kpass",
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "dXNlcjpwYXNz"
        every { bookRepository.observeByServer(1L) } returns flowOf(emptyList())
        coEvery { serverRepository.getById(1L) } returns testServer
        coEvery { bookRepository.refreshFromServer(any(), any(), any()) } returns Result.success(
            OpdsBookPage(books = emptyList()),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf("serverId" to 1L, "path" to "/api/v1/opds/catalog"),
        )
        return LibraryViewModel(savedStateHandle, bookRepository, serverRepository, readingProgressRepository, grimmoryClient, grimmoryTokenManager)
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

        assertEquals(SortOrder.TITLE, viewModel.sortOrder.value)

        viewModel.updateSortOrder(SortOrder.AUTHOR)
        assertEquals(SortOrder.AUTHOR, viewModel.sortOrder.value)

        viewModel.updateSortOrder(SortOrder.RECENT)
        assertEquals(SortOrder.RECENT, viewModel.sortOrder.value)
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
}
