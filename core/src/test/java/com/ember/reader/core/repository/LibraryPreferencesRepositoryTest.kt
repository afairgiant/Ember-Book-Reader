package com.ember.reader.core.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryPreferencesRepositoryTest {

    @Test
    fun `All round-trips`() {
        assertEquals(
            LibrarySourceFilter.All,
            LibrarySourceFilter.fromStoredString(LibrarySourceFilter.All.toStoredString())
        )
    }

    @Test
    fun `Local round-trips`() {
        assertEquals(
            LibrarySourceFilter.Local,
            LibrarySourceFilter.fromStoredString(LibrarySourceFilter.Local.toStoredString())
        )
    }

    @Test
    fun `Server id round-trips`() {
        val filter = LibrarySourceFilter.Server(serverId = 42)
        assertEquals(
            filter,
            LibrarySourceFilter.fromStoredString(filter.toStoredString())
        )
    }

    @Test
    fun `legacy SERVER value migrates to All`() {
        assertEquals(
            LibrarySourceFilter.All,
            LibrarySourceFilter.fromStoredString("SERVER")
        )
    }

    @Test
    fun `missing key falls back to All`() {
        assertEquals(LibrarySourceFilter.All, LibrarySourceFilter.fromStoredString(null))
    }

    @Test
    fun `unknown string falls back to All`() {
        assertEquals(LibrarySourceFilter.All, LibrarySourceFilter.fromStoredString("weird-value"))
    }

    @Test
    fun `malformed SERVER id falls back to All`() {
        assertEquals(LibrarySourceFilter.All, LibrarySourceFilter.fromStoredString("SERVER:abc"))
    }

    @Test
    fun `SOURCE grouping is available`() {
        // Defensive: confirms the enum value exists so callers can reference it.
        assertTrue(LibraryGroupBy.values().any { it == LibraryGroupBy.SOURCE })
    }
}
