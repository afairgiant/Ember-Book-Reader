package com.ember.reader.core.paging

import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository

/**
 * [NetworkPager] for plain OPDS Atom catalogs. Tracks the next-page path returned by the server
 * and advances a 1-based page counter alongside it for logging / `refreshFromServer` parity.
 */
class OpdsNetworkPager(
    private val server: Server,
    private val rootPath: String,
    private val repository: BookRepository
) : NetworkPager {

    private var nextPath: String? = null
    private var nextPageNumber: Int = 1

    override suspend fun refresh(): Result<NetworkPager.PageResult> {
        nextPath = null
        nextPageNumber = 1
        return repository.refreshFromServer(server, page = 1, path = rootPath)
            .onSuccess { page ->
                nextPath = page.nextPagePath
                if (page.nextPagePath != null) nextPageNumber = 2
            }
            .map {
                NetworkPager.PageResult(
                    resolvedBookIds = it.resolvedBookIds,
                    endOfPagination = it.nextPagePath == null
                )
            }
    }

    override suspend fun append(): Result<NetworkPager.PageResult> {
        val path = nextPath
            ?: return Result.success(
                NetworkPager.PageResult(emptyList(), endOfPagination = true)
            )
        val pageNum = nextPageNumber
        return repository.refreshFromServer(server, page = pageNum, path = path)
            .onSuccess { page ->
                nextPath = page.nextPagePath
                if (page.nextPagePath != null) nextPageNumber = pageNum + 1
            }
            .map {
                NetworkPager.PageResult(
                    resolvedBookIds = it.resolvedBookIds,
                    endOfPagination = it.nextPagePath == null
                )
            }
    }
}
