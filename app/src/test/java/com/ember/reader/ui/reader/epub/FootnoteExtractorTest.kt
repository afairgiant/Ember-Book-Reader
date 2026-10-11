package com.ember.reader.ui.reader.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FootnoteExtractorTest {

    private fun page(body: String) = "<html><body>$body</body></html>"

    @Test
    fun `a note marked as an aside is returned without its wrapper`() {
        val html = page("""<p>Text.</p><aside id="fn1" epub:type="footnote"><p>The <i>note</i>.</p></aside>""")

        assertEquals("<p>The <i>note</i>.</p>", FootnoteExtractor.noteHtml(html, "fn1"))
    }

    @Test
    fun `an id on an anchor inside the note stands for the paragraph around it`() {
        val html = page("""<p>Text.</p><p><a id="fn1" href="ch1.html#ref1"><sup>1</sup></a> The note.</p>""")

        val note = FootnoteExtractor.noteHtml(html, "fn1")

        assertEquals("<sup>1</sup> The note.", note)
    }

    @Test
    fun `an id on a list item gives that item only`() {
        val html = page("""<ol><li id="n1"><p>First.</p></li><li id="n2"><p>Second.</p></li></ol>""")

        assertEquals("<p>Second.</p>", FootnoteExtractor.noteHtml(html, "n2"))
    }

    @Test
    fun `links back to the text are dropped`() {
        val html = page(
            """<aside id="fn1"><p>The note. <a href="#ref1">↩</a>""" +
                """<a href="#ref1" role="doc-backlink">Back</a></p></aside>"""
        )

        val note = FootnoteExtractor.noteHtml(html, "fn1")!!

        assertEquals("<p>The note.</p>", note)
    }

    @Test
    fun `links, images and scripts never reach the note`() {
        val html = page(
            """<aside id="fn1"><p>See <a href="http://example.com">this</a>""" +
                """<img src="x.png"/><script>alert(1)</script></p></aside>"""
        )

        val note = FootnoteExtractor.noteHtml(html, "fn1")!!

        assertTrue(note.contains("See this"))
        assertFalse(note.contains("<a"))
        assertFalse(note.contains("<img"))
        assertFalse(note.contains("alert"))
    }

    @Test
    fun `a missing id gives no note`() {
        assertNull(FootnoteExtractor.noteHtml(page("<p>Text.</p>"), "fn1"))
    }

    @Test
    fun `an empty target gives no note`() {
        assertNull(FootnoteExtractor.noteHtml(page("""<p>Text.</p><div id="fn1"></div>"""), "fn1"))
    }

    @Test
    fun `a target that is a passage of the book is not a note`() {
        val chapter = "word ".repeat(1200)

        assertNull(FootnoteExtractor.noteHtml(page("""<div id="ch2"><p>$chapter</p></div>"""), "ch2"))
    }

    @Test
    fun `an id directly on the body is not a note`() {
        assertNull(FootnoteExtractor.noteHtml("""<html><body id="top"><p>Text.</p></body></html>""", "top"))
    }
}
