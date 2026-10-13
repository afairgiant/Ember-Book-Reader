package com.ember.reader.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Per-book reader preferences are stored as JSON, so a preference added later has to load
 * from rows written before it existed.
 */
class ReaderPreferencesJsonTest {

    // The same settings BookReaderPreferencesRepository uses.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `an override saved before paragraph settings existed loads with them left to the book`() {
        val saved = """{"fontSize":20.0,"lineHeight":1.8,"wordSpacing":0.0,"letterSpacing":0.0}"""

        val prefs = json.decodeFromString<ReaderPreferences>(saved)

        assertEquals(20f, prefs.fontSize)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.paragraphIndent)
    }

    @Test
    fun `paragraph settings survive a save and load`() {
        val prefs = ReaderPreferences(paragraphSpacing = 1.2f, paragraphIndent = 0f, wordSpacing = 0.4f)

        val loaded = json.decodeFromString<ReaderPreferences>(json.encodeToString(prefs))

        assertEquals(prefs, loaded)
    }
}
