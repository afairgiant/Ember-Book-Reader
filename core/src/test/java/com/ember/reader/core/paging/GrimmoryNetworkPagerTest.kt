package com.ember.reader.core.paging

import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsBookPage
import com.ember.reader.core.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class GrimmoryNetworkPagerTest {

    @MockK
    private lateinit var repository: BookRepository

    private val server = Server(
        id = 1L,
        name = "s",
        url = "http://host",
        opdsUsername = "u",
        opdsPassword = "p",
        kosyncUsername = "k",
        kosyncPassword = "k",
        isGrimmory = true
    )
    private val request = GrimmoryRequest(libraryId = 7L)

    @Test
    fun `refresh calls repo at page 0`() = runTest {
        coEvery {
            repository.upsertGrimmoryPage(server, request, page = 0, pageSize = 50)
        } returns Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = listOf("a", "b"),
                nextPagePath = "grimmory:page=1"
            )
        )
        val pager = GrimmoryNetworkPager(server, request, repository, pageSize = 50)

        val out = pager.refresh().getOrThrow()

        assertEquals(listOf("a", "b"), out.resolvedBookIds)
        assertFalse(out.endOfPagination)
    }

    @Test
    fun `append advances the page counter`() = runTest {
        coEvery {
            repository.upsertGrimmoryPage(server, request, page = 0, pageSize = 50)
        } returns Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = listOf("a"),
                nextPagePath = "grimmory:page=1"
            )
        )
        coEvery {
            repository.upsertGrimmoryPage(server, request, page = 1, pageSize = 50)
        } returns Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = listOf("b"),
                nextPagePath = null
            )
        )
        val pager = GrimmoryNetworkPager(server, request, repository, pageSize = 50)

        pager.refresh()
        val out = pager.append().getOrThrow()

        assertEquals(listOf("b"), out.resolvedBookIds)
        assertTrue(out.endOfPagination)
        coVerify { repository.upsertGrimmoryPage(server, request, page = 1, pageSize = 50) }
    }

    @Test
    fun `append without prior refresh signals end`() = runTest {
        val pager = GrimmoryNetworkPager(server, request, repository, pageSize = 50)
        val out = pager.append().getOrThrow()
        assertTrue(out.endOfPagination)
    }
}
