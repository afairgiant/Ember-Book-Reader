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
    fun `quoteCfis sends the quotes as JSON and adds the spine step to each answer`() = runTest {
        var script = ""
        val page = FakePage {
            reply("""[{"cfi":"epubcfi(/4/2,/1:16,/1:19)","matches":2},null]""")
        }
        val bridge = CfiBridge(
            evaluate = { script = it; page.evaluate(it) },
            spine = spine,
            installScript = { INSTALL },
        )

        val matches = bridge.quoteCfis(
            listOf(TextQuote(exact = "fox", before = "brown "), TextQuote(exact = "it's \"gone\"")),
        )

        assertEquals(listOf(QuoteMatch("epubcfi(/6/4[ref1]!/4/2,/1:16,/1:19)", matches = 2), null), matches)
        // The quotes travel as one JSON string argument, so their quotation marks are escaped.
        assertTrue(script.contains("""forQuotes("[{\"exact\":\"fox\",\"before\":\"brown \",\"after\":null}"""))
    }

    @Test
    fun `quoteCfis answers null for every quote when the page gives nothing usable`() = runTest {
        val quotes = listOf(TextQuote("fox"), TextQuote("dog"))

        assertEquals(listOf(null, null), bridge(FakePage { reply("null") }).quoteCfis(quotes))
        assertEquals(listOf(null, null), bridge(FakePage { reply("""[{"cfi":"0.4"}]""") }).quoteCfis(quotes))
        assertEquals(emptyList<QuoteMatch?>(), bridge(FakePage { error("not asked") }).quoteCfis(emptyList()))
    }

    @Test
    fun `resolve returns what each CFI in the page's chapter points at`() = runTest {
        val page = FakePage {
            reply("""[{"exact":"fox","before":"brown ","after":" jumps","progression":0.25},""" +
                """{"exact":"","before":"lazy ","after":"dog","progression":null}]""")
        }

        val resolved = bridge(page).resolve(listOf("epubcfi(/6/4!/4/2,/1:16,/1:19)", "epubcfi(/6/4!/4/2/1:40)"))

        assertEquals(
            listOf(
                ResolvedCfi("OEBPS/chapter1.xhtml", exact = "fox", before = "brown ", after = " jumps", progression = 0.25),
                ResolvedCfi("OEBPS/chapter1.xhtml", exact = "", before = "lazy ", after = "dog", progression = null),
            ),
            resolved,
        )
    }

    @Test
    fun `resolve answers null for a CFI from another chapter, a fake one and one the page could not resolve`() = runTest {
        val text = """{"exact":"fox","before":"","after":""}"""
        val page = FakePage { reply("[$text,$text,null]") }

        val resolved = bridge(page).resolve(
            listOf("epubcfi(/6/6!/4/2/1:0)", "0.4231", "epubcfi(/6/4!/4/2/1:0)"),
        )

        assertEquals(listOf(null, null, null), resolved)
    }

    @Test
    fun `resolve does not touch the page when no CFI is real`() = runTest {
        val page = FakePage { error("the page should not be asked") }

        assertEquals(listOf(null), bridge(page).resolve(listOf("epubcfi(/6/2)")))
        assertEquals(emptyList<ResolvedCfi?>(), bridge(page).resolve(emptyList()))
        assertTrue(page.installs == 0)
    }

    private companion object {
        const val INSTALL = "install-script;"
    }
}
