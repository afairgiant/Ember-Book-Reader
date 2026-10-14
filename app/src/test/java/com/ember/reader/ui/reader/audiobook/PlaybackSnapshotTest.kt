package com.ember.reader.ui.reader.audiobook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackSnapshotTest {

    private fun snapshot(
        trackIndex: Int = 0,
        trackCount: Int = 1,
        positionMs: Long = 0,
        durationMs: Long = 100_000,
        trackStartMs: Long = -1,
        bookLengthMs: Long = 0,
    ) = PlaybackSnapshot("book", trackIndex, trackCount, positionMs, durationMs, trackStartMs, bookLengthMs)

    @Test
    fun `one file goes by the position in it`() {
        assertEquals(0.25f, snapshot(positionMs = 25_000)!!.percentage())
    }

    @Test
    fun `tracks of known length go by where the track starts in the book`() {
        // Track 2 of a book of 10 + 30 + 60 minutes, 15 minutes in: 55 of 100 minutes.
        val percentage = snapshot(
            trackIndex = 2, trackCount = 3, positionMs = 15 * 60_000L, durationMs = 60 * 60_000L,
            trackStartMs = 40 * 60_000L, bookLengthMs = 100 * 60_000L,
        ).percentage()
        assertEquals(0.55f, percentage!!, 0.0001f)
    }

    @Test
    fun `tracks of unknown length count as equal parts`() {
        val percentage = snapshot(trackIndex = 1, trackCount = 4, positionMs = 50_000, durationMs = 100_000).percentage()
        assertEquals(0.375f, percentage!!, 0.0001f)
    }

    @Test
    fun `nothing is saved while the track's length is unknown`() {
        assertNull(snapshot(durationMs = androidx.media3.common.C.TIME_UNSET).percentage())
    }
}
