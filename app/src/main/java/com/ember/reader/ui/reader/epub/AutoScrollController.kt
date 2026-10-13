package com.ember.reader.ui.reader.epub

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moves a scroll-mode chapter up the screen at a steady speed, and on into the next
 * chapter when it reaches the bottom.
 *
 * The moving is done by a script in the page ([AutoScrollScript]); this starts and stops
 * it and looks in on it once a second. The script cannot run without a break: Readium
 * reports a new position only once the page has been still for a moment, so every
 * [CHECKPOINT_EVERY_MS] the page is held still for [CHECKPOINT_PAUSE_MS] and
 * [onCheckpoint] is given the chance to save where the reader is.
 *
 * @param evaluate runs a script in the chapter on screen and returns its result.
 * @param goToNextChapter opens the next chapter and returns once it is on screen; false
 * when there is none.
 * @param onCheckpoint called with the page held still, to save the position.
 */
internal class AutoScrollController(
    private val evaluate: suspend (String) -> String?,
    private val goToNextChapter: suspend () -> Boolean,
    private val onCheckpoint: suspend () -> Unit
) {

    /**
     * Scrolls at [speed] (CSS pixels a second, followed as it changes) until the end of
     * the book, where it returns, or until the caller cancels it. The page is left still
     * either way.
     */
    suspend fun run(speed: StateFlow<Float>) {
        try {
            coroutineScope {
                val speedChanges = launch {
                    speed.drop(1).collect { evaluate(AutoScrollScript.start(it)) }
                }
                scrollToEndOfBook(speed)
                speedChanges.cancel()
            }
        } finally {
            // The reader may be closing, in which case there is no page left to stop.
            withContext(NonCancellable) {
                runCatching {
                    evaluate(AutoScrollScript.STOP)
                    delay(CHECKPOINT_PAUSE_MS)
                    onCheckpoint()
                }
            }
        }
    }

    private suspend fun scrollToEndOfBook(speed: StateFlow<Float>) {
        evaluate(AutoScrollScript.start(speed.value))
        var sinceCheckpoint = 0L
        var pollsAtEnd = 0
        while (true) {
            delay(POLL_MS)
            sinceCheckpoint += POLL_MS
            val state = AutoScrollScript.parseState(evaluate(AutoScrollScript.STATE))
            pollsAtEnd = if (state == AutoScrollScript.State.AT_END) pollsAtEnd + 1 else 0
            when (state) {
                // The bottom has to hold for a moment: it gives the last lines time to
                // be read, and a chapter still loading can look like one already ended.
                AutoScrollScript.State.AT_END -> if (pollsAtEnd >= END_DWELL_POLLS) {
                    checkpoint()
                    if (!goToNextChapter()) return
                    evaluate(AutoScrollScript.start(speed.value))
                    sinceCheckpoint = 0L
                    pollsAtEnd = 0
                }
                // The page was loaded again (a setting changed, say) and lost the script.
                AutoScrollScript.State.GONE -> evaluate(AutoScrollScript.start(speed.value))
                AutoScrollScript.State.RUNNING -> if (sinceCheckpoint >= CHECKPOINT_EVERY_MS) {
                    checkpoint()
                    evaluate(AutoScrollScript.start(speed.value))
                    sinceCheckpoint = 0L
                }
            }
        }
    }

    private suspend fun checkpoint() {
        evaluate(AutoScrollScript.STOP)
        delay(CHECKPOINT_PAUSE_MS)
        onCheckpoint()
    }

    companion object {
        const val POLL_MS = 1_000L
        const val CHECKPOINT_EVERY_MS = 15_000L

        /** How many looks in a row must find the chapter at its bottom before it is left. */
        const val END_DWELL_POLLS = 3

        /** Readium reports a position after 100 ms of stillness; this leaves it room. */
        const val CHECKPOINT_PAUSE_MS = 200L
    }
}
