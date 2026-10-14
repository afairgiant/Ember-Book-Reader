package com.ember.reader.ui.reader.audiobook

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkipPlayerTest {

    private val inner = mockk<Player>(relaxed = true) {
        every { currentPosition } returns 100_000L
        every { duration } returns 120_000L
    }
    private val player = SkipPlayer(inner).apply { lengths = SkipLengths(backSeconds = 10, forwardSeconds = 60) }

    @Test
    fun `previous skips back by the chosen length instead of changing track`() {
        player.seekToPrevious()

        verify { inner.seekTo(90_000L) }
        verify(exactly = 0) { inner.seekToPrevious() }
    }

    @Test
    fun `next skips forward, stopping at the end of the track`() {
        player.seekToNext()

        verify { inner.seekTo(120_000L) }
        verify(exactly = 0) { inner.seekToNext() }
    }

    @Test
    fun `skipping back stops at the start of the track`() {
        every { inner.currentPosition } returns 4_000L

        player.seekBack()

        verify { inner.seekTo(0L) }
    }

    @Test
    fun `lengths without a numbered icon get the plain one`() {
        assertEquals(CommandButton.ICON_SKIP_BACK_30, skipBackIcon(30))
        assertEquals(CommandButton.ICON_SKIP_FORWARD_10, skipForwardIcon(10))
        assertEquals(CommandButton.ICON_SKIP_BACK, skipBackIcon(60))
        assertEquals(CommandButton.ICON_SKIP_FORWARD, skipForwardIcon(60))
    }
}
