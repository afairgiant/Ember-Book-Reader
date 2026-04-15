package com.ember.reader.core.database.query

/**
 * Sort orders supported by the paged library query. SQL `ORDER BY` clauses are built by
 * [LibraryQueryBuilder] — the UI selects one of these values to drive paging.
 */
enum class LibrarySortOrder(val displayName: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    RECENT("Recently Added"),
    SERIES("Series"),
}
