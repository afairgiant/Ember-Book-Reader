package com.ember.reader.core.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpubSpineTest {

    companion object {

        private val CONTAINER = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        private fun opf(spine: String, beforeSpine: String = "") = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Book</dc:title></metadata>
              <manifest>
                <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/chapter%201.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="up" href="../shared/notes.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              $beforeSpine
              <spine>
                $spine
              </spine>
            </package>
        """.trimIndent()

        private val PLAIN = opf(
            """
            <itemref idref="cover"/>
            <itemref idref="c1"/>
            <itemref idref="c2"/>
            """,
        )

        private val NON_LINEAR_FIRST = opf(
            """
            <itemref idref="cover" linear="no"/>
            <itemref idref="c1" id="ref-one"/>
            <itemref idref="c2"/>
            """,
        )
    }

    private fun parsed(opf: String) = EpubSpine.parse(opf, "OEBPS/content.opf")!!

    @Test
    fun `rootfilePath reads the package document path`() {
        assertEquals("OEBPS/content.opf", EpubSpine.rootfilePath(CONTAINER))
    }

    @Test
    fun `rootfilePath returns null for unrelated or broken XML`() {
        assertNull(EpubSpine.rootfilePath("<container/>"))
        assertNull(EpubSpine.rootfilePath("not xml"))
    }

    @Test
    fun `parse resolves manifest hrefs against the OPF directory`() {
        val spine = parsed(PLAIN)

        assertEquals(
            listOf("OEBPS/cover.xhtml", "OEBPS/text/chapter%201.xhtml", "OEBPS/text/chapter2.xhtml"),
            spine.itemrefs.map { it.href },
        )
        assertEquals(6, spine.spineStep)
    }

    @Test
    fun `parse handles an OPF at the container root and parent segments`() {
        val spine = EpubSpine.parse(opf("""<itemref idref="up"/><itemref idref="c2"/>"""), "content.opf")!!

        assertEquals(listOf("shared/notes.xhtml", "text/chapter2.xhtml"), spine.itemrefs.map { it.href })
    }

    @Test
    fun `parse keeps non-linear itemrefs in place`() {
        val spine = parsed(NON_LINEAR_FIRST)

        assertEquals(listOf(false, true, true), spine.itemrefs.map { it.linear })
        assertEquals(listOf(null, "ref-one", null), spine.itemrefs.map { it.id })
    }

    @Test
    fun `parse counts the spine step from the package's own children`() {
        val spine = EpubSpine.parse(opf("""<itemref idref="c1"/>""", beforeSpine = "<guide/>"), "content.opf")!!

        assertEquals(8, spine.spineStep)
        assertEquals("/8/2", spine.baseCfiFor("text/chapter 1.xhtml"))
    }

    @Test
    fun `parse returns null without a spine`() {
        assertNull(EpubSpine.parse("<package><manifest/></package>", "content.opf"))
        assertNull(EpubSpine.parse("not xml", "content.opf"))
    }

    @Test
    fun `hrefFor resolves by position over every itemref`() {
        val spine = parsed(NON_LINEAR_FIRST)

        // /6/4 is the second itemref. Indexing a reading order that skips the non-linear
        // cover would land on chapter2 instead.
        assertEquals("OEBPS/text/chapter%201.xhtml", spine.hrefFor("epubcfi(/6/4!/4/2/1:0)"))
        assertEquals("OEBPS/text/chapter2.xhtml", spine.hrefFor("epubcfi(/6/6!/4)"))
    }

    @Test
    fun `hrefFor prefers the itemref id and falls back to position when the id is unknown`() {
        val spine = parsed(NON_LINEAR_FIRST)

        assertEquals("OEBPS/text/chapter%201.xhtml", spine.hrefFor("epubcfi(/6/6[ref-one]!/4)"))
        assertEquals("OEBPS/text/chapter2.xhtml", spine.hrefFor("epubcfi(/6/6[gone]!/4)"))
    }

    @Test
    fun `hrefFor accepts a bare base and doubled wrapping`() {
        val spine = parsed(PLAIN)

        assertEquals("OEBPS/cover.xhtml", spine.hrefFor("/6/2"))
        assertEquals("OEBPS/text/chapter2.xhtml", spine.hrefFor("epubcfi(epubcfi(/6/6!/4))"))
    }

    @Test
    fun `hrefFor returns null for an out-of-range, odd or non-CFI value`() {
        val spine = parsed(PLAIN)

        assertNull(spine.hrefFor("epubcfi(/6/8!/4)"))
        assertNull(spine.hrefFor("epubcfi(/6/3!/4)"))
        assertNull(spine.hrefFor("0.4231"))
    }

    @Test
    fun `baseCfiFor includes the itemref id and ignores fragment and encoding differences`() {
        val spine = parsed(NON_LINEAR_FIRST)

        assertEquals("/6/4[ref-one]", spine.baseCfiFor("OEBPS/text/chapter%201.xhtml"))
        assertEquals("/6/4[ref-one]", spine.baseCfiFor("OEBPS/text/chapter 1.xhtml#para3"))
        assertEquals("/6/2", spine.baseCfiFor("OEBPS/cover.xhtml"))
        assertNull(spine.baseCfiFor("OEBPS/missing.xhtml"))
    }

    @Test
    fun `hrefForPath finds the chapter under a renderer's own prefix`() {
        val spine = parsed(PLAIN)

        assertEquals("OEBPS/text/chapter%201.xhtml", spine.hrefForPath("/publication/OEBPS/text/chapter%201.xhtml"))
        assertEquals("OEBPS/cover.xhtml", spine.hrefForPath("OEBPS/cover.xhtml"))
        assertNull(spine.hrefForPath("/publication/OEBPS/other.xhtml"))
        assertNull(spine.hrefForPath("/publication/OEBPS/not-cover.xhtml"))
    }

    @Test
    fun `hrefForPath prefers the longest matching href`() {
        val spine = EpubSpine(
            spineStep = 6,
            itemrefs = listOf("ch1.xhtml", "part2/ch1.xhtml").map {
                EpubSpine.Itemref(idref = it, id = null, linear = true, href = it)
            },
        )

        assertEquals("part2/ch1.xhtml", spine.hrefForPath("/publication/part2/ch1.xhtml"))
        assertEquals("ch1.xhtml", spine.hrefForPath("/publication/ch1.xhtml"))
    }

    @Test
    fun `baseCfiFor and hrefFor round trip`() {
        val spine = parsed(NON_LINEAR_FIRST)

        for (itemref in spine.itemrefs) {
            val base = spine.baseCfiFor(itemref.href!!)!!
            assertEquals(itemref.href, spine.hrefFor(EpubCfi.join(base, "/4")))
        }
    }
}
