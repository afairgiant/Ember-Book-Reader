package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ember.reader.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

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
        WHERE b.localPath IS NOT NULL AND rp.percentage > 0 AND rp.percentage < 1
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
