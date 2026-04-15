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
class OpdsNetworkPagerTest {

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
    )
    private val rootPath = "/catalog"

    @Test
    fun `refresh calls repo with root path page 1 and captures next key`() = runTest {
        coEvery {
            repository.refreshFromServer(server, page = 1, path = rootPath)
        } returns Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = listOf("a", "b"),
                nextPagePath = "/catalog?page=2",
            ),
        )
        val pager = OpdsNetworkPager(server, rootPath, repository)

        val out = pager.refresh().getOrThrow()

        assertEquals(listOf("a", "b"), out.resolvedBookIds)
        assertFalse(out.endOfPagination)
        coVerify { repository.refreshFromServer(server, page = 1, path = rootPath) }
    }

    @Test
    fun `append uses last captured next path`() = runTest {
        coEvery {
            repository.refreshFromServer(server, page = 1, path = rootPath)
        } returns Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = listOf("a"),
                nextPagePath = "/catalog?page=2",
            ),
        )
        coEvery {
            repository.refreshFromServer(server, page = 2, path = "/catalog?page=2")
        } returns Result.success(
            OpdsBookPage(
                books = emptyList(),
                resolvedBookIds = listOf("b"),
                nextPagePath = null,
            ),
        )
        val pager = OpdsNetworkPager(server, rootPath, repository)

        pager.refresh()
        val out = pager.append().getOrThrow()

        assertEquals(listOf("b"), out.resolvedBookIds)
        assertTrue(out.endOfPagination)
        coVerify { repository.refreshFromServer(server, page = 2, path = "/catalog?page=2") }
    }

    @Test
    fun `append with no prior refresh signals end of pagination`() = runTest {
        val pager = OpdsNetworkPager(server, rootPath, repository)
        val out = pager.append().getOrThrow()
        assertTrue(out.endOfPagination)
        assertTrue(out.resolvedBookIds.isEmpty())
    }

    @Test
    fun `failure propagates as Result failure`() = runTest {
        val boom = RuntimeException("network")
        coEvery {
            repository.refreshFromServer(server, page = 1, path = rootPath)
        } returns Result.failure(boom)
        val pager = OpdsNetworkPager(server, rootPath, repository)

        val result = pager.refresh()

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }
}
