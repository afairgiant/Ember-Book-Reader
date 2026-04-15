package com.ember.reader.core.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.ember.reader.core.database.entity.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * RemoteMediator that drives library paging. The mediator is agnostic to the underlying catalog
 * format: OPDS vs. Grimmory is a choice of [NetworkPager].
 *
 * [sessionIds] is optionally a view-scoped allowlist of book IDs (non-null for series / shelf /
 * other subcategory views, null for unscoped catalog roots). On REFRESH the mediator resets it
 * to empty; on APPEND it unions in newly-fetched IDs. The DAO query reads the same flow so the
 * Room PagingSource restricts rows to the subset of books the server returned for this view.
 *
 * No RemoteKeys table: OPDS uses an opaque next-path URL held in memory and Grimmory uses a
 * simple page counter. Across process death, paging will restart at page 0/1, which is fine for
 * this UI — there's no "jump back to row 3000" requirement.
 */
@OptIn(ExperimentalPagingApi::class)
class LibraryRemoteMediator(
    private val networkPager: NetworkPager,
    private val sessionIds: MutableStateFlow<Set<String>?>
) : RemoteMediator<Int, BookEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, BookEntity>
    ): MediatorResult = when (loadType) {
        LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
        LoadType.REFRESH -> {
            if (sessionIds.value != null) sessionIds.value = emptySet()
            fetchAndMerge { networkPager.refresh() }
        }
        LoadType.APPEND -> fetchAndMerge { networkPager.append() }
    }

    private suspend fun fetchAndMerge(
        fetch: suspend () -> Result<NetworkPager.PageResult>
    ): MediatorResult = fetch().fold(
        onSuccess = { page ->
            if (sessionIds.value != null) {
                sessionIds.update { (it ?: emptySet()) + page.resolvedBookIds }
            }
            MediatorResult.Success(endOfPaginationReached = page.endOfPagination)
        },
        onFailure = { t -> MediatorResult.Error(LibraryLoadError.fromThrowable(t)) }
    )
}
