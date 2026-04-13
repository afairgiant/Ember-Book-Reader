package com.ember.reader.ui.library

import com.ember.reader.core.grimmory.ReadStatus

/**
 * Ephemeral sort/filter state for Grimmory library views. Lives on the ViewModel; resets on every
 * navigation. Pushed to the server via `GrimmoryAppClient.getBooks` — client-side filtering would
 * break pagination.
 *
 * Grimmory's `buildSort` only honours `title`, `series`, and `addedOn`; other keys silently fall
 * back to `addedOn`. Ember exposes only the three it actually supports.
 */
data class GrimmoryFilter(
    val sort: GrimmorySortKey = GrimmorySortKey.TITLE,
    val direction: SortDirection = SortDirection.ASC,
    val status: ReadStatus? = null,
    val minRating: Int? = null,
    val maxRating: Int? = null,
    val authors: String? = null,
    val language: String? = null
) {
    /** True if any filter is set OR the sort differs from the default (title asc). */
    val isActive: Boolean
        get() = sort != GrimmorySortKey.TITLE ||
            direction != SortDirection.ASC ||
            status != null ||
            minRating != null ||
            maxRating != null ||
            !authors.isNullOrBlank() ||
            !language.isNullOrBlank()

    /** True if any of the book-restricting filters (not sort) are set. */
    val hasRestrictiveFilters: Boolean
        get() = status != null ||
            minRating != null ||
            maxRating != null ||
            !authors.isNullOrBlank() ||
            !language.isNullOrBlank()
}

enum class GrimmorySortKey(val apiValue: String) {
    ADDED("addedOn"),
    TITLE("title"),
    SERIES("series")
}

enum class SortDirection(val apiValue: String) {
    ASC("asc"),
    DESC("desc")
}
