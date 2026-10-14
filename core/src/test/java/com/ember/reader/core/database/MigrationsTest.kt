package com.ember.reader.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationsTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EmberDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate13To14_addsIndicesAndKeepsData() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                "INSERT INTO books(id,title,format,addedAt) VALUES('x','t','EPUB',0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            14,
            true,
            EmberDatabase.MIGRATION_13_14
        )

        migrated.query("SELECT COUNT(*) FROM books").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        val indexNames = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='books'"
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0))
            }
        }

        assertTrue(
            "expected serverId_title composite index, got $indexNames",
            indexNames.any { it.contains("serverId_title") }
        )
        assertTrue(
            "expected serverId_addedAt composite index, got $indexNames",
            indexNames.any { it.contains("serverId_addedAt") }
        )
        assertTrue(
            "expected serverId_author composite index, got $indexNames",
            indexNames.any { it.contains("serverId_author") }
        )
        assertTrue(
            "expected serverId_series_seriesIndex composite index, got $indexNames",
            indexNames.any { it.contains("serverId_series_seriesIndex") }
        )
        assertTrue(
            "expected localPath index, got $indexNames",
            indexNames.any { it.contains("index_books_localPath") }
        )
        assertTrue(
            "expected format index, got $indexNames",
            indexNames.any { it.contains("index_books_format") }
        )
    }

    @Test
    fun migrate15To16_addsAccentColorSlotColumn() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                "INSERT INTO servers(" +
                    "name,url,opdsUsername,kosyncUsername,grimmoryUsername," +
                    "isGrimmory,canMoveOrganizeFiles,opdsEnabled,kosyncEnabled" +
                    ") VALUES('S','https://s','u','u','',0,0,1,1)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            true,
            EmberDatabase.MIGRATION_15_16
        )

        migrated.query("SELECT accentColorSlot FROM servers").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("expected NULL default for accentColorSlot", c.isNull(0))
        }
    }

    @Test
    fun migrate16To17_addsMetadataColumnsToBooks() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            db.execSQL(
                "INSERT INTO books(id,title,format,addedAt) VALUES('b1','T','EPUB',0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            17,
            true,
            EmberDatabase.MIGRATION_16_17
        )

        migrated.query("SELECT fileSizeKb,ageRating,contentRating FROM books").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("expected NULL default for fileSizeKb", c.isNull(0))
            assertTrue("expected NULL default for ageRating", c.isNull(1))
            assertTrue("expected NULL default for contentRating", c.isNull(2))
        }
    }

    @Test
    fun migrate17To18_addsCfiColumnToHighlightsAndBookmarks() {
        helper.createDatabase(TEST_DB, 17).use { db ->
            db.execSQL("INSERT INTO books(id,title,format,addedAt) VALUES('b1','T','EPUB',0)")
            db.execSQL(
                "INSERT INTO highlights(bookId,locatorJson,color,createdAt,updatedAt) VALUES('b1','{}','YELLOW',0,0)"
            )
            db.execSQL("INSERT INTO bookmarks(bookId,locatorJson,createdAt,updatedAt) VALUES('b1','{}',0,0)")
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            true,
            EmberDatabase.MIGRATION_17_18
        )

        for (table in listOf("highlights", "bookmarks")) {
            migrated.query("SELECT cfi FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue("expected NULL default for $table.cfi", c.isNull(0))
            }
        }
    }

    @Test
    fun migrate18To19_addsCfiColumnToReadingProgress() {
        helper.createDatabase(TEST_DB, 18).use { db ->
            db.execSQL("INSERT INTO books(id,title,format,addedAt) VALUES('b1','T','EPUB',0)")
            db.execSQL(
                "INSERT INTO reading_progress(bookId,percentage,lastReadAt,needsSync) VALUES('b1',0.5,0,0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            19,
            true,
            EmberDatabase.MIGRATION_18_19
        )

        migrated.query("SELECT cfi FROM reading_progress").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("expected NULL default for reading_progress.cfi", c.isNull(0))
        }
    }

    @Test
    fun migrate19To20_mergesDuplicateServerBooksAndAddsUniqueIndex() {
        helper.createDatabase(TEST_DB, 19).use { db ->
            db.execSQL(
                "INSERT INTO servers(id,name,url,opdsUsername,kosyncUsername,grimmoryUsername,isGrimmory," +
                    "opdsEnabled,kosyncEnabled,canMoveOrganizeFiles) VALUES(1,'s','http://x','u','u','u',1,1,1,0)"
            )
            // The same server book twice: an older row with progress and a highlight, and a
            // newer, downloaded one with newer progress. A local book stays out of it.
            db.execSQL("INSERT INTO books(id,serverId,opdsEntryId,title,format,addedAt) VALUES('old',1,'e1','T','EPUB',0)")
            db.execSQL(
                "INSERT INTO books(id,serverId,opdsEntryId,title,format,addedAt,localPath) " +
                    "VALUES('downloaded',1,'e1','T','EPUB',5,'/books/t.epub')"
            )
            db.execSQL("INSERT INTO books(id,title,format,addedAt) VALUES('local','L','EPUB',0)")
            db.execSQL(
                "INSERT INTO highlights(bookId,locatorJson,color,createdAt,updatedAt) VALUES('old','{}','YELLOW',0,0)"
            )
            db.execSQL("INSERT INTO reading_progress(bookId,percentage,lastReadAt,needsSync) VALUES('old',0.2,100,0)")
            db.execSQL(
                "INSERT INTO reading_progress(bookId,percentage,lastReadAt,needsSync) VALUES('downloaded',0.6,200,0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 20, true, EmberDatabase.MIGRATION_19_20)

        migrated.query("SELECT id FROM books ORDER BY id").use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertEquals(listOf("downloaded", "local"), ids)
        }
        migrated.query("SELECT bookId FROM highlights").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("downloaded", c.getString(0))
        }
        migrated.query("SELECT bookId, percentage FROM reading_progress").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("downloaded", c.getString(0))
            assertEquals(0.6f, c.getFloat(1))
        }
    }

    private companion object {
        const val TEST_DB = "ember-migrations-test"
    }
}
