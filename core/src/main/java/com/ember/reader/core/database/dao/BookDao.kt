package com.ember.reader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.ember.reader.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /**
     * Paged query for the library screen. The SQL is composed by `LibraryQueryBuilder` so sort,
     * filter, search, and subcategory-scoping all resolve at the database layer.
     */
    @RawQuery(observedEntities = [BookEntity::class])
    fun pageBooksForView(query: SupportSQLiteQuery): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getByIds(ids: Set<String>): List<BookEntity>

    @Query("SELECT * FROM books WHERE serverId = :serverId ORDER BY title ASC")
    fun observeByServer(serverId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE serverId = :serverId")
    suspend fun getBooksByServerId(serverId: Long): List<BookEntity>

    @Query("SELECT * FROM books WHERE serverId IS NULL ORDER BY title ASC")
    fun observeLocalBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE localPath IS NOT NULL ORDER BY title ASC")
    fun observeDownloadedBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE localPath IS NOT NULL AND serverId IS NOT NULL ORDER BY title ASC")
    fun observeServerDownloads(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE serverId = :serverId AND fileHash IS NOT NULL AND localPath IS NOT NULL")
    suspend fun getDownloadedBooksForServer(serverId: Long): List<BookEntity>

    @Query(
        """
        SELECT b.* FROM books b
        INNER JOIN reading_progress rp ON b.id = rp.bookId
        WHERE b.localPath IS NOT NULL AND rp.percentage > 0 AND rp.percentage < 0.95
        ORDER BY rp.lastReadAt DESC
        LIMIT 10
        """
    )
    fun observeRecentlyReading(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE opdsEntryId = :opdsEntryId AND serverId = :serverId")
    suspend fun getByOpdsEntryId(opdsEntryId: String, serverId: Long): BookEntity?

    /**
     * Updates the row for the server book [opdsEntryId] on [serverId] with [onExisting], or
     * inserts [onNew] when there is none, and returns the row's id. The lookup and the write
     * are one transaction, so two refreshes of the same book at once cannot both insert it.
     */
    @Transaction
    suspend fun upsertServerBook(
        serverId: Long,
        opdsEntryId: String,
        onNew: () -> BookEntity,
        onExisting: (BookEntity) -> BookEntity,
    ): String {
        val existing = getByOpdsEntryId(opdsEntryId, serverId)
        if (existing != null) {
            update(onExisting(existing))
            return existing.id
        }
        val book = onNew()
        insert(book)
        return book.id
    }

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun getByFileHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE serverId = :serverId AND (fileHash = :hash OR title LIKE '%' || :title || '%' COLLATE NOCASE) LIMIT 1")
    suspend fun getByServerAndHashOrTitle(serverId: Long, hash: String, title: String): BookEntity?

    @Query("SELECT * FROM books WHERE localPath IS NOT NULL AND serverId IS NOT NULL AND downloadedAt < :before")
    suspend fun getOldServerDownloads(before: java.time.Instant): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET localPath = :localPath, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateLocalPath(id: String, localPath: String?, downloadedAt: java.time.Instant?)

    @Query("UPDATE books SET fileHash = :hash WHERE id = :id")
    suspend fun updateFileHash(id: String, hash: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Makes the book [toId] the one for the file of the book [fromId], and removes [fromId].
     *
     * Everything kept for [fromId] goes with it first, since deleting it would otherwise
     * delete its highlights, bookmarks, progress and reading history with it. Progress goes
     * only when it is newer than [toId]'s own; reader settings only when [toId] has none.
     */
    @Transaction
    suspend fun relink(
        fromId: String,
        toId: String,
        localPath: String,
        downloadedAt: java.time.Instant?,
        fileHash: String?,
    ) {
        moveHighlights(fromId, toId)
        moveBookmarks(fromId, toId)
        moveReadingSessions(fromId, toId)
        deleteOlderProgress(fromId, toId)
        moveProgress(fromId, toId)
        moveReaderPreferences(fromId, toId)
        deleteReaderPreferences(fromId)
        updateLocalPath(toId, localPath, downloadedAt)
        fileHash?.let { updateFileHash(toId, it) }
        deleteById(fromId)
    }

    @Query("UPDATE highlights SET bookId = :toId WHERE bookId = :fromId")
    suspend fun moveHighlights(fromId: String, toId: String)

    @Query("UPDATE bookmarks SET bookId = :toId WHERE bookId = :fromId")
    suspend fun moveBookmarks(fromId: String, toId: String)

    @Query("UPDATE reading_sessions SET bookId = :toId WHERE bookId = :fromId")
    suspend fun moveReadingSessions(fromId: String, toId: String)

    @Query(
        """
        DELETE FROM reading_progress WHERE bookId = :toId
        AND lastReadAt < (SELECT lastReadAt FROM reading_progress WHERE bookId = :fromId)
        """
    )
    suspend fun deleteOlderProgress(fromId: String, toId: String)

    /** Moves [fromId]'s progress to [toId], unless [toId] still has its own. */
    @Query(
        """
        UPDATE OR IGNORE reading_progress
        SET bookId = :toId, serverId = (SELECT serverId FROM books WHERE id = :toId)
        WHERE bookId = :fromId
        """
    )
    suspend fun moveProgress(fromId: String, toId: String)

    @Query("UPDATE OR IGNORE book_reader_preferences SET bookId = :toId WHERE bookId = :fromId")
    suspend fun moveReaderPreferences(fromId: String, toId: String)

    @Query("DELETE FROM book_reader_preferences WHERE bookId = :bookId")
    suspend fun deleteReaderPreferences(bookId: String)

    @Query("SELECT opdsEntryId FROM books WHERE serverId = :serverId AND opdsEntryId IS NOT NULL")
    suspend fun getOpdsEntryIdsForServer(serverId: Long): List<String>

    @Query("DELETE FROM books WHERE serverId = :serverId AND localPath IS NULL AND opdsEntryId IN (:opdsEntryIds)")
    suspend fun deleteServerBooksByOpdsEntryIds(serverId: Long, opdsEntryIds: List<String>)

    @Query("UPDATE books SET serverId = NULL WHERE serverId = :serverId AND localPath IS NOT NULL AND opdsEntryId IN (:opdsEntryIds)")
    suspend fun detachServerAssociation(serverId: Long, opdsEntryIds: List<String>)

    @Query(
        """
        SELECT * FROM books
        WHERE (serverId = :serverId OR serverId IS NULL)
        AND (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
        ORDER BY title ASC
        """
    )
    fun search(serverId: Long?, query: String): Flow<List<BookEntity>>
}
