package com.ember.reader.core.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.model.BookFormat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalPagingApi::class)
class LibraryRemoteMediatorTest {

    private fun emptyState(): PagingState<Int, BookEntity> = PagingState(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 50),
        leadingPlaceholderCount = 0
    )

    private fun book(id: String): BookEntity = BookEntity(
        id = id,
        title = id,
        format = BookFormat.EPUB,
        addedAt = Instant.EPOCH
    )

    @Test
    fun `prepend short-circuits as end-of-pagination`() = runTest {
        val pager = mockk<NetworkPager>()
        val mediator = LibraryRemoteMediator(pager, MutableStateFlow<Set<String>?>(null))

        val result = mediator.load(LoadType.PREPEND, emptyState())

        val success = assertInstanceOf(RemoteMediator.MediatorResult.Success::class.java, result)
        assertTrue(success.endOfPaginationReached)
    }

    @Test
    fun `refresh resets scoped sessionIds to empty and merges returned ids`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.refresh() } returns Result.success(
            NetworkPager.PageResult(listOf("a", "b"), endOfPagination = false)
        )
        val sessionIds = MutableStateFlow<Set<String>?>(setOf("stale"))
        val mediator = LibraryRemoteMediator(pager, sessionIds)

        val result = mediator.load(LoadType.REFRESH, emptyState())

        assertInstanceOf(RemoteMediator.MediatorResult.Success::class.java, result)
        assertEquals(setOf("a", "b"), sessionIds.value)
        coVerify { pager.refresh() }
    }

    @Test
    fun `refresh leaves unscoped sessionIds as null`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.refresh() } returns Result.success(
            NetworkPager.PageResult(listOf("a"), endOfPagination = true)
        )
        val sessionIds = MutableStateFlow<Set<String>?>(null)
        val mediator = LibraryRemoteMediator(pager, sessionIds)

        mediator.load(LoadType.REFRESH, emptyState())

        assertNull(sessionIds.value)
    }

    @Test
    fun `append unions newly-fetched ids into scoped sessionIds`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.append() } returns Result.success(
            NetworkPager.PageResult(listOf("c", "d"), endOfPagination = false)
        )
        val sessionIds = MutableStateFlow<Set<String>?>(setOf("a", "b"))
        val mediator = LibraryRemoteMediator(pager, sessionIds)

        mediator.load(LoadType.APPEND, emptyState())

        assertEquals(setOf("a", "b", "c", "d"), sessionIds.value)
    }

    @Test
    fun `append end-of-pagination surfaces through MediatorResult`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.append() } returns Result.success(
            NetworkPager.PageResult(emptyList(), endOfPagination = true)
        )
        val mediator = LibraryRemoteMediator(pager, MutableStateFlow<Set<String>?>(null))

        val result = mediator.load(LoadType.APPEND, emptyState())

        val success = assertInstanceOf(RemoteMediator.MediatorResult.Success::class.java, result)
        assertTrue(success.endOfPaginationReached)
    }

    @Test
    fun `pager failure surfaces as MediatorResult Error wrapping LibraryLoadError`() = runTest {
        val pager = mockk<NetworkPager>()
        val boom = RuntimeException("network down")
        coEvery { pager.refresh() } returns Result.failure(boom)
        val mediator = LibraryRemoteMediator(pager, MutableStateFlow<Set<String>?>(null))

        val result = mediator.load(LoadType.REFRESH, emptyState())

        val err = assertInstanceOf(RemoteMediator.MediatorResult.Error::class.java, result)
        assertInstanceOf(LibraryLoadError.Network::class.java, err.throwable)
        assertEquals(boom, (err.throwable as LibraryLoadError.Network).original)
    }

    /**
     * Regression guard for the shelf-shows-empty race: without an explicit invalidate after the
     * sessionIds update, Room's auto-invalidation (triggered by the book upserts inside the
     * fetch) snapshots the still-empty sessionIds and the shelf view stays frozen empty.
     * The callback must fire AFTER sessionIds has been populated so the refactored PagingSource
     * reads the allowlist.
     */
    @Test
    fun `scoped refresh invalidates PagingSource after populating sessionIds`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.refresh() } returns Result.success(
            NetworkPager.PageResult(listOf("a", "b"), endOfPagination = true)
        )
        val sessionIds = MutableStateFlow<Set<String>?>(emptySet())
        var sessionIdsAtInvalidation: Set<String>? = null
        val mediator = LibraryRemoteMediator(
            networkPager = pager,
            sessionIds = sessionIds,
            invalidatePagingSource = { sessionIdsAtInvalidation = sessionIds.value }
        )

        mediator.load(LoadType.REFRESH, emptyState())

        assertEquals(setOf("a", "b"), sessionIdsAtInvalidation)
    }

    @Test
    fun `scoped append invalidates PagingSource after unioning ids`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.append() } returns Result.success(
            NetworkPager.PageResult(listOf("c"), endOfPagination = true)
        )
        val sessionIds = MutableStateFlow<Set<String>?>(setOf("a", "b"))
        var invalidateCount = 0
        val mediator = LibraryRemoteMediator(
            networkPager = pager,
            sessionIds = sessionIds,
            invalidatePagingSource = { invalidateCount++ }
        )

        mediator.load(LoadType.APPEND, emptyState())

        assertEquals(1, invalidateCount)
        assertEquals(setOf("a", "b", "c"), sessionIds.value)
    }

    @Test
    fun `unscoped refresh does not invalidate PagingSource`() = runTest {
        val pager = mockk<NetworkPager>()
        coEvery { pager.refresh() } returns Result.success(
            NetworkPager.PageResult(listOf("a"), endOfPagination = true)
        )
        var invalidateCount = 0
        val mediator = LibraryRemoteMediator(
            networkPager = pager,
            sessionIds = MutableStateFlow<Set<String>?>(null),
            invalidatePagingSource = { invalidateCount++ }
        )

        mediator.load(LoadType.REFRESH, emptyState())

        assertEquals(0, invalidateCount)
    }
}
