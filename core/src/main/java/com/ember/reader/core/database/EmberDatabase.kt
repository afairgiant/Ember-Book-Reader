package com.ember.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ember.reader.core.database.converter.Converters
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.entity.ReadingProgressEntity
import com.ember.reader.core.database.entity.ServerEntity

@Database(
    entities = [
        ServerEntity::class,
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EmberDatabase : RoomDatabase() {

    companion object {
        /**
         * Migration 1→2: Change books foreign key from CASCADE to SET_NULL
         * so deleting a server doesn't destroy downloaded books.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support ALTER FOREIGN KEY, so recreate the table
                db.execSQL(
                    """
                    CREATE TABLE books_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        serverId INTEGER,
                        opdsEntryId TEXT,
                        title TEXT NOT NULL,
                        author TEXT,
                        description TEXT,
                        coverUrl TEXT,
                        downloadUrl TEXT,
                        localPath TEXT,
                        format TEXT NOT NULL,
                        fileHash TEXT,
                        series TEXT,
                        seriesIndex REAL,
                        addedAt INTEGER NOT NULL,
                        downloadedAt INTEGER,
                        FOREIGN KEY (serverId) REFERENCES servers(id) ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO books_new SELECT * FROM books")
                db.execSQL("DROP TABLE books")
                db.execSQL("ALTER TABLE books_new RENAME TO books")
                db.execSQL("CREATE INDEX index_books_serverId ON books(serverId)")
                db.execSQL("CREATE INDEX index_books_opdsEntryId ON books(opdsEntryId)")
            }
        }
    }
    abstract fun serverDao(): ServerDao
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
}
