package com.ember.reader.ui.editmetadata

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.grimmory.GrimmoryBookMetadata
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.grimmory.MetadataClient
import com.ember.reader.core.grimmory.MetadataProvider
import com.ember.reader.core.grimmory.searchableProviders
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** Covers which metadata providers the search form offers on load. */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class EditMetadataViewModelProvidersTest {

    @MockK
    private lateinit var bookRepository: BookRepository

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var metadataClient: MetadataClient

    private val dispatcher = StandardTestDispatcher()

    private val server = Server(
        id = 7L,
        name = "Grimmory",
        url = "http://localhost:8080",
        opdsUsername = "u",
        opdsPassword = "p",
        kosyncUsername = "ku",
        kosyncPassword = "kp",
        isGrimmory = true,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val book = mockk<Book>(relaxed = true)
        every { book.serverId } returns server.id
        every { book.grimmoryBookId } returns 101L
        coEvery { bookRepository.getById("book-1") } returns book
        coEvery { serverRepository.getById(server.id) } returns server
        coEvery { metadataClient.getBookMetadata(server.url, server.id, 101L) } returns
            Result.success(GrimmoryBookMetadata(bookId = 101L, title = "Dune"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loadSearchForm(): SearchForm {
        val viewModel = EditMetadataViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "book-1")),
            bookRepository = bookRepository,
            serverRepository = serverRepository,
            metadataClient = metadataClient,
            applicationScope = this,
        )
        advanceUntilIdle()
        return (viewModel.uiState.value as EditMetadataUiState.Success).state.searchForm
    }

    @Test
    fun `search form offers and preselects the providers enabled on the server`() = runTest(dispatcher) {
        val enabled = listOf(MetadataProvider.Amazon, MetadataProvider.GoodReads, MetadataProvider.Audible)
        coEvery { metadataClient.getEnabledSearchProviders(server.url, server.id) } returns Result.success(enabled)

        val form = loadSearchForm()

        assertEquals(enabled, form.availableProviders)
        assertEquals(enabled.toSet(), form.providers)
        assertEquals("Dune", form.title)
    }

    @Test
    fun `search form falls back to the default providers when the server has no provider list`() =
        runTest(dispatcher) {
            coEvery { metadataClient.getEnabledSearchProviders(server.url, server.id) } returns
                Result.failure(GrimmoryHttpException(404, "Not Found"))

            val form = loadSearchForm()

            assertEquals(searchableProviders, form.availableProviders)
            assertEquals(searchableProviders.toSet(), form.providers)
        }

    @Test
    fun `search form offers nothing when the server has no supported provider enabled`() =
        runTest(dispatcher) {
            coEvery { metadataClient.getEnabledSearchProviders(server.url, server.id) } returns
                Result.success(emptyList())

            val form = loadSearchForm()

            assertTrue(form.availableProviders.isEmpty())
            assertTrue(form.providers.isEmpty())
        }
}
