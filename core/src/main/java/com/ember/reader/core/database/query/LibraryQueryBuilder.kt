package com.ember.reader.core.database.query

import androidx.sqlite.db.SimpleSQLiteQuery
import com.ember.reader.core.model.BookFormat

/**
 * Builds the SQL for the paged library query consumed by `BookDao.pageBooksForView`. The query
 * pushes sort, format filter, downloaded-only, free-text search, and session-scoped ID gating
 * into SQLite so Paging 3 can re-query on filter changes without touching the view layer.
 *
 * Sort keys are whitelisted via [LibrarySortOrder]; user-supplied search text is bound as a LIKE
 * parameter (never concatenated) with `%`/`_`/`\` escaped via `ESCAPE '\'`.
 */
object LibraryQueryBuilder {

    data class Inputs(
        val serverId: Long,
        val sort: LibrarySortOrder,
        val formatFilter: BookFormat?,
        val downloadedOnly: Boolean,
        val query: String,
        /**
         * Session-scoped ID allowlist: `null` for no scoping (catalog root), `emptySet()` to
         * force an empty result (subcategory view before mediator has fetched any IDs), or a
         * non-empty set to `id IN (?, ?, ...)` gate the rows.
         */
        val sessionIds: Set<String>?,
    )

    fun build(inputs: Inputs): SimpleSQLiteQuery {
        if (inputs.sessionIds != null && inputs.sessionIds.isEmpty()) {
            return SimpleSQLiteQuery("SELECT * FROM books WHERE 0", emptyArray())
        }

        val args = mutableListOf<Any>()
        val sql = StringBuilder("SELECT * FROM books WHERE serverId = ?")
        args.add(inputs.serverId)

        if (inputs.sessionIds != null) {
            val placeholders = inputs.sessionIds.joinToString(", ") { "?" }
            sql.append(" AND id IN (").append(placeholders).append(")")
            args.addAll(inputs.sessionIds)
        }

        if (inputs.formatFilter != null) {
            sql.append(" AND format = ?")
            args.add(inputs.formatFilter.name)
        }

        if (inputs.downloadedOnly) {
            sql.append(" AND localPath IS NOT NULL")
        }

        val trimmedQuery = inputs.query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val pattern = "%" + escapeLike(trimmedQuery) + "%"
            sql.append(" AND (title LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\')")
            args.add(pattern)
            args.add(pattern)
        }

        sql.append(" ORDER BY ").append(orderByClause(inputs.sort))

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun orderByClause(sort: LibrarySortOrder): String = when (sort) {
        LibrarySortOrder.TITLE -> "title COLLATE NOCASE ASC"
        LibrarySortOrder.AUTHOR -> "author IS NULL, author COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        LibrarySortOrder.RECENT -> "addedAt DESC"
        LibrarySortOrder.SERIES -> "series IS NULL, series COLLATE NOCASE ASC, seriesIndex ASC, title COLLATE NOCASE ASC"
    }

    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
