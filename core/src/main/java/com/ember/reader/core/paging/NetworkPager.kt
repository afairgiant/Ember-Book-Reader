package com.ember.reader.core.paging

/**
 * One-page fetch strategy for the library RemoteMediator. The mediator owns the Paging
 * lifecycle and delegates actual network calls here; OPDS and Grimmory each get their own
 * implementation since they have different cursor shapes (opaque next-path URL vs. integer
 * page offset).
 *
 * Implementations are stateful — they track the current cursor internally — and are expected
 * to be short-lived: a new pager is constructed for each PagingData flow emission so that
 * filter/sort changes start from page 1 with no carryover.
 */
interface NetworkPager {
    data class PageResult(
        val resolvedBookIds: List<String>,
        val endOfPagination: Boolean,
    )

    /** Resets pagination and fetches the first page. */
    suspend fun refresh(): Result<PageResult>

    /**
     * Fetches the next page using the cursor captured by the last successful call. If no page
     * has been fetched yet, or the last page was the terminal one, returns a result with
     * [PageResult.endOfPagination] set to true so the mediator can stop appending.
     */
    suspend fun append(): Result<PageResult>
}
