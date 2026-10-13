package com.ember.reader.ui.reader.audiobook

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * What the audiobook sleep timer is set to. It is kept by the playback service, not the
 * player screen, so that it still stops the book after the screen has been closed.
 */
sealed interface SleepTimer {

    data object Off : SleepTimer

    /** Stops at [endsAtElapsedMs], a time on the device's elapsed-realtime clock. */
    data class Timed(val endsAtElapsedMs: Long) : SleepTimer

    /**
     * Stops at the end of the chapter that was playing when it was set: at [stopPositionMs]
     * in the track at [stopIndex], or at the end of that track when there is no position.
     */
    data class EndOfChapter(val stopIndex: Int, val stopPositionMs: Long?) : SleepTimer

    /** Whether playback at [positionMs] in track [index] has reached the point to stop at. */
    fun isDue(nowElapsedMs: Long, index: Int, positionMs: Long): Boolean = when (this) {
        Off -> false
        is Timed -> nowElapsedMs >= endsAtElapsedMs
        is EndOfChapter -> index > stopIndex ||
            (index == stopIndex && stopPositionMs != null && positionMs >= stopPositionMs)
    }

    /**
     * How loud to play, 0 to 1. A timed stop fades out over its last [FADE_MS] so that it
     * does not cut off mid-word at full volume; a chapter's end is a natural stop already.
     */
    fun volume(nowElapsedMs: Long): Float = when (this) {
        is Timed -> ((endsAtElapsedMs - nowElapsedMs).toFloat() / FADE_MS).coerceIn(0f, 1f)
        else -> 1f
    }

    fun toBundle(): Bundle = Bundle().apply {
        when (val timer = this@SleepTimer) {
            Off -> putString(KEY_KIND, KIND_OFF)
            is Timed -> {
                putString(KEY_KIND, KIND_TIMED)
                putLong(KEY_ENDS_AT, timer.endsAtElapsedMs)
            }
            is EndOfChapter -> {
                putString(KEY_KIND, KIND_CHAPTER)
                putInt(KEY_STOP_INDEX, timer.stopIndex)
                timer.stopPositionMs?.let { putLong(KEY_STOP_POSITION, it) }
            }
        }
    }

    companion object {
        const val FADE_MS = 10_000L

        /** The lengths offered, in minutes. */
        val MINUTES = listOf(5, 10, 15, 30, 45, 60)

        /** Sets the timer; its arguments are a timer's [toBundle]. Off cancels it. */
        val SET_COMMAND = SessionCommand("com.ember.reader.audiobook.SET_SLEEP_TIMER", Bundle.EMPTY)

        private const val KEY_KIND = "sleep_timer_kind"
        private const val KEY_ENDS_AT = "sleep_timer_ends_at"
        private const val KEY_STOP_INDEX = "sleep_timer_stop_index"
        private const val KEY_STOP_POSITION = "sleep_timer_stop_position"
        private const val KIND_OFF = "off"
        private const val KIND_TIMED = "timed"
        private const val KIND_CHAPTER = "chapter"

        /** Reads a timer back from [toBundle]; anything else is no timer. */
        fun fromBundle(bundle: Bundle?): SleepTimer = when (bundle?.getString(KEY_KIND)) {
            KIND_TIMED -> Timed(bundle.getLong(KEY_ENDS_AT))
            KIND_CHAPTER -> EndOfChapter(
                stopIndex = bundle.getInt(KEY_STOP_INDEX),
                stopPositionMs = if (bundle.containsKey(KEY_STOP_POSITION)) bundle.getLong(KEY_STOP_POSITION) else null
            )
            else -> Off
        }
    }
}

/** "29:05", or "1:02:10" from an hour up: the time a sleep timer has left. */
internal fun formatTimeLeft(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
