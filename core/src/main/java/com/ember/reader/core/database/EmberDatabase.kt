package com.ember.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ember.reader.core.database.converter.Converters
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.dao.BookReaderPreferencesDao
import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.dao.CatalogEntryPreferenceDao
import com.ember.reader.core.database.dao.DictionaryDao
import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.dao.ReadingSessionDao
import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.dao.SyncStatusDao
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.database.entity.BookReaderPreferencesEntity
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.entity.CatalogEntryPreferenceEntity
import com.ember.reader.core.database.entity.DictionaryEntryEntity
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.entity.ReadingProgressEntity
import com.ember.reader.core.database.entity.ReadingSessionEntity
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.database.entity.SyncStatusEntity

@Database(
    entities = [
        ServerEntity::class,
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReadingSessionEntity::class,
        DictionaryEntryEntity::class,
        BookReaderPreferencesEntity::class,
        CatalogEntryPreferenceEntity::class,
        SyncStatusEntity::class
    ],
    version = 12,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class EmberDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN grimmoryUsername TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE servers ADD COLUMN isGrimmory INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE reading_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId TEXT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        startProgress REAL NOT NULL,
                        endProgress REAL NOT NULL,
                        FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX index_reading_sessions_bookId ON reading_sessions(bookId)")
                db.execSQL("CREATE INDEX index_reading_sessions_startTime ON reading_sessions(startTime)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN language TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN subjects TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN pageCount INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN publishedDate TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE highlights ADD COLUMN selectedText TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE highlights ADD COLUMN remoteId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE highlights ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE highlights ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("UPDATE highlights SET updatedAt = createdAt")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN remoteId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("UPDATE bookmarks SET updatedAt = createdAt")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE dictionary_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        phonetic TEXT,
                        definitions TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX index_dictionary_cache_word ON dictionary_cache(word)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE book_reader_preferences (
                        bookId TEXT NOT NULL PRIMARY KEY,
                        preferencesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE servers ADD COLUMN canMoveOrganizeFiles INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE catalog_entry_preferences (
                        serverId INTEGER NOT NULL,
                        entryId TEXT NOT NULL,
                        hidden INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT ${Int.MAX_VALUE},
                        PRIMARY KEY (serverId, entryId)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE sync_status (
                        serverId INTEGER NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        lastAttemptAt INTEGER NOT NULL,
                        statusCode INTEGER,
                        detail TEXT
                    )
                    """.trimIndent()
                )
            }
        }

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
                    """.trimIndent()
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
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun bookReaderPreferencesDao(): BookReaderPreferencesDao
    abstract fun catalogEntryPreferenceDao(): CatalogEntryPreferenceDao
    abstract fun syncStatusDao(): SyncStatusDao
}
