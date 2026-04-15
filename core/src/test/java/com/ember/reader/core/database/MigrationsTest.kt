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

    private companion object {
        const val TEST_DB = "ember-migrations-test"
    }
}
