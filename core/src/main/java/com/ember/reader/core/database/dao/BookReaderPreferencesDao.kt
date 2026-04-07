package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ember.reader.core.database.entity.BookReaderPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookReaderPreferencesDao {

    @Query("SELECT * FROM book_reader_preferences WHERE bookId = :bookId LIMIT 1")
    suspend fun get(bookId: String): BookReaderPreferencesEntity?

    @Query("SELECT * FROM book_reader_preferences WHERE bookId = :bookId LIMIT 1")
    fun observe(bookId: String): Flow<BookReaderPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookReaderPreferencesEntity)

    @Query("DELETE FROM book_reader_preferences WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
