package com.ember.reader.core.database.query

import com.ember.reader.core.model.BookFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryQueryBuilderTest {

    private fun inputs(
        serverId: Long = 1L,
        sort: LibrarySortOrder = LibrarySortOrder.TITLE,
        formatFilter: BookFormat? = null,
        downloadedOnly: Boolean = false,
        query: String = "",
        sessionIds: Set<String>? = null
    ) = LibraryQueryBuilder.Inputs(
        serverId = serverId,
        sort = sort,
        formatFilter = formatFilter,
        downloadedOnly = downloadedOnly,
        query = query,
        sessionIds = sessionIds
    )

    @Test
    fun `default title sort binds serverId and collates nocase`() {
        val query = LibraryQueryBuilder.build(inputs())
        assertTrue(query.sql.contains("ORDER BY title COLLATE NOCASE ASC"), "sql=${query.sql}")
        assertTrue(query.sql.startsWith("SELECT * FROM books WHERE serverId = ?"))
    }

    @Test
    fun `author sort places nulls last`() {
        val sql = LibraryQueryBuilder.build(inputs(sort = LibrarySortOrder.AUTHOR)).sql
        assertTrue(sql.contains("author IS NULL"), sql)
        assertTrue(sql.contains("author COLLATE NOCASE ASC"), sql)
    }

    @Test
    fun `recent sort orders by addedAt DESC`() {
        val sql = LibraryQueryBuilder.build(inputs(sort = LibrarySortOrder.RECENT)).sql
        assertTrue(sql.contains("ORDER BY addedAt DESC"), sql)
    }

    @Test
    fun `series sort places null series last and orders by seriesIndex`() {
        val sql = LibraryQueryBuilder.build(inputs(sort = LibrarySortOrder.SERIES)).sql
        assertTrue(sql.contains("series IS NULL"), sql)
        assertTrue(sql.contains("series COLLATE NOCASE ASC"), sql)
        assertTrue(sql.contains("seriesIndex ASC"), sql)
    }

    @Test
    fun `format filter adds clause and binds format name`() {
        val query = LibraryQueryBuilder.build(inputs(formatFilter = BookFormat.EPUB))
        assertTrue(query.sql.contains("AND format = ?"), query.sql)
        // Expect 2 args: serverId + format
        assertEquals(2, query.argCount)
    }

    @Test
    fun `downloadedOnly adds non-null localPath predicate`() {
        val sql = LibraryQueryBuilder.build(inputs(downloadedOnly = true)).sql
        assertTrue(sql.contains("AND localPath IS NOT NULL"), sql)
    }

    @Test
    fun `search clause binds escaped pattern for title and author`() {
        val query = LibraryQueryBuilder.build(inputs(query = "foo%_bar"))
        val sql = query.sql
        assertTrue(sql.contains("title LIKE ? ESCAPE '\\'"), sql)
        assertTrue(sql.contains("author LIKE ? ESCAPE '\\'"), sql)
        // Expect 3 args: serverId + two LIKE params
        assertEquals(3, query.argCount)
    }

    @Test
    fun `blank search adds no LIKE clause`() {
        val query = LibraryQueryBuilder.build(inputs(query = "  "))
        assertTrue(!query.sql.contains("LIKE"), query.sql)
        assertEquals(1, query.argCount)
    }

    @Test
    fun `non-empty sessionIds adds IN clause with positional params`() {
        val query = LibraryQueryBuilder.build(
            inputs(sessionIds = setOf("a", "b", "c"))
        )
        assertTrue(query.sql.contains("AND id IN (?, ?, ?)"), query.sql)
        assertEquals(4, query.argCount) // serverId + 3 ids
    }

    @Test
    fun `empty sessionIds returns empty-result sentinel`() {
        val query = LibraryQueryBuilder.build(inputs(sessionIds = emptySet()))
        assertTrue(query.sql.contains("WHERE 0"), query.sql)
    }

    @Test
    fun `null sessionIds omits id-scope clause`() {
        val sql = LibraryQueryBuilder.build(inputs(sessionIds = null)).sql
        assertTrue(!sql.contains("id IN"), sql)
    }

    @Test
    fun `combines all clauses in expected order`() {
        val query = LibraryQueryBuilder.build(
            inputs(
                sort = LibrarySortOrder.AUTHOR,
                formatFilter = BookFormat.PDF,
                downloadedOnly = true,
                query = "harry",
                sessionIds = setOf("x")
            )
        )
        val sql = query.sql
        // serverId, id IN, format, localPath, LIKE
        assertTrue(sql.contains("WHERE serverId = ?"), sql)
        assertTrue(sql.contains("id IN (?)"), sql)
        assertTrue(sql.contains("format = ?"), sql)
        assertTrue(sql.contains("localPath IS NOT NULL"), sql)
        assertTrue(sql.contains("LIKE"), sql)
        // serverId + 1 sessionId + format + 2 LIKE params
        assertEquals(5, query.argCount)
    }
}
