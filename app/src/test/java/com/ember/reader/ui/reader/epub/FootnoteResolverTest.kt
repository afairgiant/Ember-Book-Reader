package com.ember.reader.ui.reader.epub

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FootnoteResolverTest {

    private val chapter = """<html><body><p><a id="fn1" href="#r1">1</a> The note.</p></body></html>"""

    private fun resolver(
        pageAnswer: String?,
        chapters: Map<String, String> = mapOf("text/notes.html" to chapter),
        scripts: MutableList<String> = mutableListOf(),
    ) = FootnoteResolver(
        evaluate = { script ->
            scripts += script
            pageAnswer
        },
        readChapter = { chapters[it] },
        referenceScript = { "function (path, fragment) { return true }" },
    )

    @Test
    fun `a reference to a note gives the note`() = runTest {
        val note = resolver(pageAnswer = "true").noteFor("text/notes.html", "fn1")

        assertEquals("1 The note.", note)
    }

    @Test
    fun `a link the page does not call a reference is left alone`() = runTest {
        assertNull(resolver(pageAnswer = "false").noteFor("text/notes.html", "fn1"))
        assertNull(resolver(pageAnswer = null).noteFor("text/notes.html", "fn1"))
    }

    @Test
    fun `a page script that fails leaves the link alone`() = runTest {
        assertNull(resolver(pageAnswer = "\"TypeError: x is not a function\"").noteFor("text/notes.html", "fn1"))
    }

    @Test
    fun `a reference whose target cannot be read or found is left alone`() = runTest {
        assertNull(resolver(pageAnswer = "true", chapters = emptyMap()).noteFor("text/notes.html", "fn1"))
        assertNull(resolver(pageAnswer = "true").noteFor("text/notes.html", "missing"))
    }

    @Test
    fun `the page is asked about the link's target, quoted safely`() = runTest {
        val scripts = mutableListOf<String>()

        resolver(pageAnswer = "false", scripts = scripts).noteFor("text/it's.html", "fn\"1")

        assertTrue(scripts.single().contains(""""text/it's.html","fn\"1""""))
    }

    @Test
    fun `the bundled script is a single function expression`() {
        val source = File("src/main/assets/notes/note-reference.js").readText()
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n").trim()

        assertTrue(source.startsWith("function (targetPath, targetFragment) {"))
        assertTrue(source.endsWith("}"))
    }
}
