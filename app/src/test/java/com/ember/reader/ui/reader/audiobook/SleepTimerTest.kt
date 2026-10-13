package com.ember.reader.ui.reader.audiobook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepTimerTest {

    @Test
    fun `a timed timer is due once its time has come, wherever playback is`() {
        val timer = SleepTimer.Timed(endsAtElapsedMs = 60_000)

        assertFalse(timer.isDue(nowElapsedMs = 59_999, index = 0, positionMs = 0))
        assertTrue(timer.isDue(nowElapsedMs = 60_000, index = 3, positionMs = 5))
    }

    @Test
    fun `a timed timer fades out over its last ten seconds`() {
        val timer = SleepTimer.Timed(endsAtElapsedMs = 60_000)

        assertEquals(1f, timer.volume(nowElapsedMs = 30_000))
        assertEquals(1f, timer.volume(nowElapsedMs = 50_000))
        assertEquals(0.5f, timer.volume(nowElapsedMs = 55_000))
        assertEquals(0f, timer.volume(nowElapsedMs = 61_000))
    }

    @Test
    fun `end of chapter is due at the chapter's end in its track, or once that track is over`() {
        val timer = SleepTimer.EndOfChapter(stopIndex = 2, stopPositionMs = 90_000)

        assertFalse(timer.isDue(nowElapsedMs = 0, index = 2, positionMs = 89_999))
        assertTrue(timer.isDue(nowElapsedMs = 0, index = 2, positionMs = 90_000))
        assertTrue(timer.isDue(nowElapsedMs = 0, index = 3, positionMs = 0))
        assertFalse(timer.isDue(nowElapsedMs = 0, index = 1, positionMs = 500_000))
    }

    @Test
    fun `without a chapter end it waits for the track to finish, at full volume`() {
        val timer = SleepTimer.EndOfChapter(stopIndex = 0, stopPositionMs = null)

        assertFalse(timer.isDue(nowElapsedMs = 0, index = 0, positionMs = 9_999_999))
        assertTrue(timer.isDue(nowElapsedMs = 0, index = 1, positionMs = 0))
        assertEquals(1f, timer.volume(nowElapsedMs = 0))
    }

    @Test
    fun `no timer is never due`() {
        assertFalse(SleepTimer.Off.isDue(nowElapsedMs = Long.MAX_VALUE, index = 9, positionMs = Long.MAX_VALUE))
    }

    @Test
    fun `time left is shown in minutes and seconds, with hours when there are any`() {
        assertEquals("5:00", formatTimeLeft(300_000))
        assertEquals("4:59", formatTimeLeft(298_500))
        assertEquals("0:01", formatTimeLeft(1))
        assertEquals("0:00", formatTimeLeft(-5))
        assertEquals("1:00:00", formatTimeLeft(3_600_000))
    }
}
