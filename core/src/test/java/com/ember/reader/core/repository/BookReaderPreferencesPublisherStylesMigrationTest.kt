package com.ember.reader.core.repository

import com.ember.reader.core.database.entity.BookReaderPreferencesEntity
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.testutil.FakeBookReaderPreferencesDao
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookReaderPreferencesPublisherStylesMigrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun row(bookId: String, prefs: ReaderPreferences) =
        BookReaderPreferencesEntity(
            bookId = bookId,
            preferencesJson = json.encodeToString(prefs),
            updatedAt = Instant.EPOCH,
        )

    @Test
    fun `disablePublisherStyles flips the flag and preserves other fields`() {
        val migration = BookReaderPreferencesPublisherStylesMigration(mockk(), mockk())
        val original = json.encodeToString(
            ReaderPreferences(publisherStyles = true, fontSize = 22f)
        )

        val migrated = migration.disablePublisherStyles(original)

        val decoded = json.decodeFromString<ReaderPreferences>(migrated!!)
        assertFalse(decoded.publisherStyles)
        assertEquals(22f, decoded.fontSize)
    }

    @Test
    fun `disablePublisherStyles returns null on malformed json`() {
        val migration = BookReaderPreferencesPublisherStylesMigration(mockk(), mockk())
        assertNull(migration.disablePublisherStyles("{not valid json"))
    }

    @Test
    fun `migrateIfNeeded rewrites overrides to publisherStyles false once`() = runTest {
        val dao = FakeBookReaderPreferencesDao()
        dao.seed(row("book-1", ReaderPreferences(publisherStyles = true, fontSize = 22f)))
        val appPrefs = mockk<AppPreferencesRepository>()
        coEvery { appPrefs.hasMigratedBookPublisherStyles() } returns false
        coEvery { appPrefs.markBookPublisherStylesMigrated() } just Runs

        BookReaderPreferencesPublisherStylesMigration(dao, appPrefs).migrateIfNeeded()

        val stored = json.decodeFromString<ReaderPreferences>(dao.all.single().preferencesJson)
        assertFalse(stored.publisherStyles)
        assertEquals(22f, stored.fontSize)
        coVerify { appPrefs.markBookPublisherStylesMigrated() }
    }

    @Test
    fun `migrateIfNeeded does nothing when already migrated`() = runTest {
        val dao = FakeBookReaderPreferencesDao()
        dao.seed(row("book-1", ReaderPreferences(publisherStyles = true)))
        val appPrefs = mockk<AppPreferencesRepository>()
        coEvery { appPrefs.hasMigratedBookPublisherStyles() } returns true

        BookReaderPreferencesPublisherStylesMigration(dao, appPrefs).migrateIfNeeded()

        val stored = json.decodeFromString<ReaderPreferences>(dao.all.single().preferencesJson)
        assertTrue(stored.publisherStyles) // untouched
        coVerify(exactly = 0) { appPrefs.markBookPublisherStylesMigrated() }
    }
}
