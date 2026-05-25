package com.ember.reader.core.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublisherStylesResetMigrationTest {

    private val publisherStylesKey = booleanPreferencesKey("publisher_styles")
    private val guardKey = booleanPreferencesKey("publisher_styles_reset_v1")

    @Test
    fun `removes persisted publisher_styles and sets the guard flag`() = runTest {
        val before = mutablePreferencesOf(publisherStylesKey to true)

        assertTrue(publisherStylesResetMigration.shouldMigrate(before))

        val after = publisherStylesResetMigration.migrate(before)
        assertFalse(after.contains(publisherStylesKey))
        assertEquals(true, after[guardKey])
    }

    @Test
    fun `does not migrate once the guard flag is set`() = runTest {
        val migrated = preferencesOf(guardKey to true)
        assertFalse(publisherStylesResetMigration.shouldMigrate(migrated))
    }
}
