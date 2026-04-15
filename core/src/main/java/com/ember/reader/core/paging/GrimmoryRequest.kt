package com.ember.reader.core.paging

import com.ember.reader.core.grimmory.GrimmoryFilter
import com.ember.reader.core.grimmory.GrimmorySortKey
import com.ember.reader.core.grimmory.SortDirection
import java.net.URLDecoder

/**
 * A fully-resolved Grimmory library request: catalog-path query params merged with user-selected
 * sort/filter state, plus an optional search-text override from the library search bar.
 *
 * Path params (libraryId, shelfId, magicShelfId, seriesName, status, search) are parsed from the
 * catalogPath string used by the library navigation. When both the path and the filter set
 * `status`, the path wins — the user has navigated into a scoped view. Search is the inverse:
 * a non-blank `searchOverride` (from the search bar) wins over any path-level `search` param.
 */
data class GrimmoryRequest(
    val libraryId: Long? = null,
    val shelfId: Long? = null,
    val magicShelfId: Long? = null,
    val seriesName: String? = null,
    val status: String? = null,
    val search: String? = null,
    val sort: String = GrimmorySortKey.ADDED.apiValue,
    val dir: String = SortDirection.DESC.apiValue,
    val minRating: Int? = null,
    val maxRating: Int? = null,
    val authors: String? = null,
    val language: String? = null
) {
    companion object {
        fun fromCatalogPath(
            catalogPath: String,
            filter: GrimmoryFilter,
            searchOverride: String? = null
        ): GrimmoryRequest {
            val paramString = catalogPath.removePrefix("grimmory:")
            val params = paramString.split("&")
                .filter { "=" in it }
                .associate {
                    val (key, value) = it.split("=", limit = 2)
                    key to URLDecoder.decode(value, "UTF-8")
                }
            val resolvedSearch = searchOverride
                ?.takeIf { it.isNotBlank() }
                ?: params["search"]
            return GrimmoryRequest(
                libraryId = params["libraryId"]?.toLongOrNull(),
                shelfId = params["shelfId"]?.toLongOrNull(),
                magicShelfId = params["magicShelfId"]?.toLongOrNull(),
                seriesName = params["seriesName"],
                status = params["status"] ?: filter.status?.name,
                search = resolvedSearch,
                sort = filter.sort.apiValue,
                dir = filter.direction.apiValue,
                minRating = filter.minRating,
                maxRating = filter.maxRating,
                authors = filter.authors,
                language = filter.language
            )
        }
    }
}
