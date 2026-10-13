package com.ember.reader.ui.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeEmptyTest {

    @Test
    fun `a book in progress leaves nothing to offer`() {
        assertNull(homeEmptyOf(reading = 1, servers = 0, downloaded = 0))
        assertNull(homeEmptyOf(reading = 2, servers = 1, downloaded = 5))
    }

    @Test
    fun `no server and no book is a welcome`() {
        assertEquals(HomeEmpty.Welcome, homeEmptyOf(reading = 0, servers = 0, downloaded = 0))
    }

    @Test
    fun `a server or a book with nothing started is nothing in progress`() {
        assertEquals(
            HomeEmpty.NothingInProgress(hasServer = true),
            homeEmptyOf(reading = 0, servers = 1, downloaded = 0)
        )
        assertEquals(
            HomeEmpty.NothingInProgress(hasServer = false),
            homeEmptyOf(reading = 0, servers = 0, downloaded = 3)
        )
    }
}
