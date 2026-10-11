package com.ember.reader.ui.reader.epub

import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
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
    private class FakePage(private val answer: (script: String) -> String?) {
        var installed = false
        val scripts = mutableListOf<String>()

        fun evaluate(script: String): String? {
            scripts += script
            if (script == INSTALL) {
                installed = true
                return "null"
            }
            if (!installed) return notInstalled(script)
            return answer(script)
        }

        private fun notInstalled(script: String): String =
            JsonPrimitive(Regex("return '([^']+)'").find(script)!!.groupValues[1]).toString()
    }

    private fun bridge(
        page: FakePage,
        href: String? = "OEBPS/chapter1.xhtml",
        spine: EpubSpine? = this.spine,
    ) = CfiBridge(
        evaluate = page::evaluate,
        currentHref = { href },
        spine = spine,
        installScript = { INSTALL },
    )

    /** What the WebView returns for a script that returned `JSON.stringify(value)`. */
    private fun returned(json: String) = JsonPrimitive(json).toString()

    private fun returnedString(value: String) = returned(JsonPrimitive(value).toString())

    @Test
    fun `selectionCfi passes the chapter's base and returns the script's CFI`() = runTest {
        val page = FakePage { returnedString("epubcfi(/6/4[ref1]!/4/2,/1:0,/1:5)") }

        val cfi = bridge(page).selectionCfi()

        assertEquals("epubcfi(/6/4[ref1]!/4/2,/1:0,/1:5)", cfi)
        assertTrue(page.scripts.last().contains("""forSelection("epubcfi(/6/4[ref1])")"""))
    }

    @Test
    fun `the script is installed on first use and not again`() = runTest {
        val page = FakePage { returnedString("epubcfi(/6/4[ref1]!/4/2/1:0)") }
        val bridge = bridge(page)

        bridge.visibleStartCfi()
        bridge.visibleStartCfi()

        assertEquals(1, page.scripts.count { it == INSTALL })
        assertEquals(4, page.scripts.size)
    }

    @Test
    fun `no CFI when nothing is selected or the script failed`() = runTest {
        assertNull(bridge(FakePage { returned("null") }).selectionCfi())
        assertNull(bridge(FakePage { "null" }).selectionCfi())
        assertNull(bridge(FakePage { null }).selectionCfi())
    }

    @Test
    fun `a result that is not a real CFI is discarded`() = runTest {
        assertNull(bridge(FakePage { returnedString("epubcfi(/6/4[ref1])") }).visibleStartCfi())
        assertNull(bridge(FakePage { returnedString("0.42") }).visibleStartCfi())
    }

    @Test
    fun `no CFI and no script call without a spine or for a chapter outside it`() = runTest {
        val page = FakePage { returnedString("epubcfi(/6/4!/4/2/1:0)") }

        assertNull(bridge(page, spine = null).selectionCfi())
        assertNull(bridge(page, href = "OEBPS/unknown.xhtml").selectionCfi())
        assertNull(bridge(page, href = null).selectionCfi())
        assertTrue(page.scripts.isEmpty())
    }

    @Test
    fun `resolve returns the text for a CFI in the chapter on screen`() = runTest {
        val page = FakePage { returned("""{"exact":"fox","before":"brown ","after":" jumps"}""") }

        val resolved = bridge(page).resolve("epubcfi(/6/4!/4/2,/1:16,/1:19)")

        assertEquals(ResolvedCfi(exact = "fox", before = "brown ", after = " jumps"), resolved)
    }

    @Test
    fun `resolve refuses a CFI from another chapter, a placeholder and empty text`() = runTest {
        val page = FakePage { returned("""{"exact":"","before":"","after":""}""") }
        val bridge = bridge(page)

        assertNull(bridge.resolve("epubcfi(/6/6!/4/2/1:0)"))
        assertNull(bridge.resolve("epubcfi(/6/2)"))
        assertTrue(page.scripts.isEmpty())
        assertNull(bridge.resolve("epubcfi(/6/4!/4/2/1:0)"))
    }

    private companion object {
        const val INSTALL = "install-script"
    }
}
