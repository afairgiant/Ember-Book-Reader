package com.ember.reader.ui.reader.epub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterPullControllerTest {

    @Test
    fun `the page's answer is read as the swipe it stands for`() {
        assertEquals(ChapterPullScript.Pull.NEXT, ChapterPullScript.parsePull("\"next\""))
        assertEquals(ChapterPullScript.Pull.PREVIOUS, ChapterPullScript.parsePull("\"previous\""))
        assertNull(ChapterPullScript.parsePull("\"\""))
        assertNull(ChapterPullScript.parsePull("null"))
        assertNull(ChapterPullScript.parsePull(null))
    }

    @Test
    fun `a swipe past the end opens the next chapter, and one past the start the one before`() = runTest {
        val answers = ArrayDeque(listOf("\"\"", "\"next\"", "\"\"", "\"previous\""))
        val moves = mutableListOf<String>()
        val controller = ChapterPullController(
            evaluate = { answers.removeFirstOrNull() ?: "\"\"" },
            goToNextChapter = { moves += "next" },
            goToPreviousChapter = { moves += "previous" }
        )

        val job = launch { controller.run() }
        runCurrent()
        assertTrue(moves.isEmpty())
        advanceTimeBy(1_000)
        job.cancelAndJoin()

        assertEquals(listOf("next", "previous"), moves)
    }

    @Test
    fun `a page that is not there to ask changes nothing`() = runTest {
        var moves = 0
        val controller = ChapterPullController({ null }, { moves++ }, { moves++ })

        val job = launch { controller.run() }
        advanceTimeBy(1_000)
        job.cancelAndJoin()

        assertEquals(0, moves)
    }
}
