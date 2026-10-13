package com.ember.reader.ui.reader.epub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoScrollControllerTest {

    /** A chapter that answers the controller's scripts; [state] is what it reports. */
    private class FakePage(var state: String? = "\"running\"") {
        val scripts = mutableListOf<String>()
        var moving = false
        var starts = 0

        fun evaluate(script: String): String? {
            scripts += script
            when {
                script == AutoScrollScript.STOP -> moving = false
                script == AutoScrollScript.STATE -> return state
                else -> {
                    moving = true
                    starts++
                }
            }
            return "\"ok\""
        }
    }

    private val speed = AutoScrollScript.DEFAULT_SPEED

    @Test
    fun `it starts the page moving and leaves it still when cancelled`() = runTest {
        val page = FakePage()
        var checkpoints = 0
        val controller = AutoScrollController(page::evaluate, { true }, { checkpoints++ })

        val job = launch { controller.run(speed) }
        runCurrent()
        assertTrue(page.moving)

        job.cancelAndJoin()

        assertFalse(page.moving)
        assertEquals(1, checkpoints, "the position is saved when it stops")
    }

    @Test
    fun `it holds the page still for a checkpoint and moves it on again`() = runTest {
        val page = FakePage()
        var checkpoints = 0
        var movingAtCheckpoint: Boolean? = null
        val controller = AutoScrollController(page::evaluate, { true }, {
            checkpoints++
            movingAtCheckpoint = page.moving
        })

        val job = launch { controller.run(speed) }
        advanceTimeBy(AutoScrollController.CHECKPOINT_EVERY_MS + AutoScrollController.CHECKPOINT_PAUSE_MS + 1)

        assertEquals(1, checkpoints)
        assertEquals(false, movingAtCheckpoint)
        assertTrue(page.moving)
        job.cancelAndJoin()
    }

    @Test
    fun `a chapter is left only once its bottom has held, and the next one is started`() = runTest {
        val page = FakePage(state = "\"end\"")
        var chaptersOpened = 0
        val controller = AutoScrollController(
            evaluate = page::evaluate,
            goToNextChapter = {
                chaptersOpened++
                page.state = "\"running\""
                true
            },
            onCheckpoint = {}
        )

        val job = launch { controller.run(speed) }
        advanceTimeBy(AutoScrollController.POLL_MS * (AutoScrollController.END_DWELL_POLLS - 1) + 1)
        assertEquals(0, chaptersOpened)

        advanceTimeBy(AutoScrollController.POLL_MS + AutoScrollController.CHECKPOINT_PAUSE_MS)
        assertEquals(1, chaptersOpened)
        assertEquals(2, page.starts)
        assertTrue(page.moving)
        job.cancelAndJoin()
    }

    @Test
    fun `it returns at the end of the book with the page still`() = runTest {
        val page = FakePage(state = "\"end\"")
        val controller = AutoScrollController(page::evaluate, { false }, {})

        val job = launch { controller.run(speed) }
        advanceTimeBy(AutoScrollController.POLL_MS * AutoScrollController.END_DWELL_POLLS + 1_000)

        assertTrue(job.isCompleted)
        assertFalse(page.moving)
    }

    @Test
    fun `a page that lost the script is started again`() = runTest {
        val page = FakePage(state = "\"gone\"")
        val controller = AutoScrollController(page::evaluate, { true }, {})

        val job = launch { controller.run(speed) }
        advanceTimeBy(AutoScrollController.POLL_MS + 1)

        assertEquals(2, page.starts)
        job.cancelAndJoin()
    }

    @Test
    fun `the page's answer is read with or without quotes, and no answer means gone`() {
        assertEquals(AutoScrollScript.State.RUNNING, AutoScrollScript.parseState("\"running\""))
        assertEquals(AutoScrollScript.State.AT_END, AutoScrollScript.parseState("end"))
        assertEquals(AutoScrollScript.State.GONE, AutoScrollScript.parseState(null))
        assertEquals(AutoScrollScript.State.GONE, AutoScrollScript.parseState("null"))
    }
}
