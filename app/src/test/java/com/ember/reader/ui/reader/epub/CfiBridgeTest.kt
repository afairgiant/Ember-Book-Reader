package com.ember.reader.ui.reader.epub

import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CfiBridgeTest {

    private val spine = EpubSpine(
        spineStep = 6,
        itemrefs = listOf(
            EpubSpine.Itemref(idref = "cover", id = null, linear = false, href = "OEBPS/cover.xhtml"),
            EpubSpine.Itemref(idref = "c1", id = "ref1", linear = true, href = "OEBPS/chapter1.xhtml"),
            EpubSpine.Itemref(idref = "c2", id = null, linear = true, href = "OEBPS/chapter2.xhtml"),
        ),
    )

    /** A stand-in for the WebView: answers `emberCfi` calls once the install script has run. */
    private class FakePage(private val answer: () -> String?) {
        var installed = false
        var installs = 0

        fun evaluate(script: String): String? {
            if (script.startsWith(INSTALL)) {
                installed = true
                installs++
            }
            return if (installed) answer() else "false"
        }
    }

    private fun bridge(page: FakePage) = CfiBridge(
        evaluate = page::evaluate,
        spine = spine,
        installScript = { INSTALL },
    )

    /** What the page returns: its own path and the function's result, as JSON. */
    private fun reply(value: String, path: String = "/publication/OEBPS/chapter1.xhtml") =
        """{"path":"$path","value":$value}"""

    @Test
    fun `selectionCfi adds the spine step of the chapter the page reports`() = runTest {
        val page = FakePage { reply(""""epubcfi(/4/2,/1:0,/1:5)"""") }

        assertEquals("epubcfi(/6/4[ref1]!/4/2,/1:0,/1:5)", bridge(page).selectionCfi())
    }

    @Test
    fun `the spine step follows the page, whichever chapter it is`() = runTest {
        val page = FakePage { reply(""""epubcfi(/4/2/1:0)"""", path = "/publication/OEBPS/chapter2.xhtml") }

        assertEquals("epubcfi(/6/6!/4/2/1:0)", bridge(page).visibleStartCfi())
    }

    @Test
    fun `the script is installed on first use and not again`() = runTest {
        val page = FakePage { reply(""""epubcfi(/4/2/1:0)"""") }
        val bridge = bridge(page)

        bridge.visibleStartCfi()
        bridge.visibleStartCfi()

        assertEquals(1, page.installs)
    }

    @Test
    fun `no CFI when nothing is selected, the function threw or the page returned garbage`() = runTest {
        assertNull(bridge(FakePage { reply("null") }).selectionCfi())
        assertNull(bridge(FakePage { """{"error":"TypeError: boom"}""" }).selectionCfi())
        assertNull(bridge(FakePage { "null" }).selectionCfi())
        assertNull(bridge(FakePage { "not json" }).selectionCfi())
        assertNull(bridge(FakePage { null }).selectionCfi())
    }

    @Test
    fun `no CFI when the script never installs`() = runTest {
        val page = FakePage { "false" }

        assertNull(bridge(page).selectionCfi())
        assertEquals(1, page.installs)
    }

    @Test
    fun `no CFI for a page that is not a chapter of the book`() = runTest {
        val page = FakePage { reply(""""epubcfi(/4/2/1:0)"""", path = "/publication/OEBPS/unknown.xhtml") }

        assertNull(bridge(page).selectionCfi())
    }

    @Test
    fun `a result that does not make a real CFI is discarded`() = runTest {
        assertNull(bridge(FakePage { reply(""""0.42"""") }).visibleStartCfi())
        assertNull(bridge(FakePage { reply("""{"not":"a string"}""") }).visibleStartCfi())
    }

    @Test
    fun `resolve returns the text for a CFI in the page's chapter`() = runTest {
        val page = FakePage { reply("""{"exact":"fox","before":"brown ","after":" jumps"}""") }

        val resolved = bridge(page).resolve("epubcfi(/6/4!/4/2,/1:16,/1:19)")

        assertEquals(ResolvedCfi(exact = "fox", before = "brown ", after = " jumps"), resolved)
    }

    @Test
    fun `resolve refuses a CFI from another chapter and empty text`() = runTest {
        val found = FakePage { reply("""{"exact":"fox","before":"","after":""}""") }
        val empty = FakePage { reply("""{"exact":"","before":"","after":""}""") }

        assertNull(bridge(found).resolve("epubcfi(/6/6!/4/2/1:0)"))
        assertNull(bridge(empty).resolve("epubcfi(/6/4!/4/2/1:0)"))
    }

    @Test
    fun `resolve does not touch the page for something that is not a real CFI`() = runTest {
        val page = FakePage { error("the page should not be asked") }

        assertNull(bridge(page).resolve("epubcfi(/6/2)"))
        assertTrue(page.installs == 0)
    }

    private companion object {
        const val INSTALL = "install-script;"
    }
}
