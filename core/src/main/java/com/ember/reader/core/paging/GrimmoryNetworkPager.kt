package com.ember.reader.core.paging

import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository

/**
 * [NetworkPager] for Grimmory's App API. Grimmory paginates by integer offset (0-based), which
 * lives entirely client-side here — the server returns `hasNext=true` as long as more pages
 * exist.
 */
class GrimmoryNetworkPager(
    private val server: Server,
    private val request: GrimmoryRequest,
    private val repository: BookRepository,
    private val pageSize: Int = 50,
) : NetworkPager {

    private var nextPage: Int? = null

    override suspend fun refresh(): Result<NetworkPager.PageResult> {
        nextPage = null
        return repository.upsertGrimmoryPage(server, request, page = 0, pageSize = pageSize)
            .onSuccess { page ->
                nextPage = if (page.nextPagePath != null) 1 else null
            }
            .map {
                NetworkPager.PageResult(
                    resolvedBookIds = it.resolvedBookIds,
                    endOfPagination = it.nextPagePath == null,
                )
            }
    }

    override suspend fun append(): Result<NetworkPager.PageResult> {
        val page = nextPage
            ?: return Result.success(
                NetworkPager.PageResult(emptyList(), endOfPagination = true),
            )
        return repository.upsertGrimmoryPage(server, request, page = page, pageSize = pageSize)
            .onSuccess { result ->
                nextPage = if (result.nextPagePath != null) page + 1 else null
            }
            .map {
                NetworkPager.PageResult(
                    resolvedBookIds = it.resolvedBookIds,
                    endOfPagination = it.nextPagePath == null,
                )
            }
    }
}
