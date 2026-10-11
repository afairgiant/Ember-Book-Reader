package com.ember.reader.ui.reader.epub

import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncedPositionPlacerTest {

    private val spine = EpubSpine(
        spineStep = 6,
        itemrefs = listOf(
            EpubSpine.Itemref(idref = "c1", id = null, linear = true, href = "OEBPS/chapter1.xhtml"),
            EpubSpine.Itemref(idref = "c2", id = null, linear = true, href = "OEBPS/chapter2.xhtml"),
        ),
    )

    /** A page showing chapter 1 that answers every resolve with [answers]. */
    private fun placer(answers: String, asked: MutableList<String> = mutableListOf()): SyncedPositionPlacer {
        val bridge = CfiBridge(
            evaluate = { script ->
                asked += script
                """{"path":"/publication/OEBPS/chapter1.xhtml","value":$answers}"""
            },
            spine = spine,
            installScript = { "" },
        )
        return SyncedPositionPlacer(bridge, spine) { "readium/$it" }
    }

    /** A locator as sync stores it for an item pulled from the server. */
    private fun synced(cfi: String, text: String? = null) =
        """{"href":"","type":"application/xhtml+xml","locations":{"fragments":["$cfi"]}""" +
            (text?.let { ""","text":{"highlight":"$it"}""" } ?: "") + "}"

    private val found = """{"exact":"fox","before":"brown ","after":" jumps","progression":0.25}"""

    @Test
    fun `a synced highlight in the chapter on screen gets its chapter, context and position`() = runTest {
        val placed = placer("[$found]").place(
            currentHref = "OEBPS/chapter1.xhtml",
            locators = mapOf(7L to synced("epubcfi(/6/2!/4/2,/1:16,/1:19)", text = "fox")),
            requireText = true,
        )

        val json = JSONObject(placed.getValue(7L))
        assertEquals("readium/OEBPS/chapter1.xhtml", json.getString("href"))
        assertEquals("brown ", json.getJSONObject("text").getString("before"))
        assertEquals(" jumps", json.getJSONObject("text").getString("after"))
        assertEquals(0.25, json.getJSONObject("locations").getDouble("progression"))
        assertEquals(
            "epubcfi(/6/2!/4/2,/1:16,/1:19)",
            json.getJSONObject("locations").getJSONArray("fragments").getString(0),
        )
    }

    @Test
    fun `items for other chapters, placed items and local items are not sent to the page`() = runTest {
        val asked = mutableListOf<String>()
        val placed = placer("[]", asked).place(
            currentHref = "OEBPS/chapter1.xhtml",
            locators = mapOf(
                1L to synced("epubcfi(/6/4!/4/2/1:0)"),
                2L to """{"href":"OEBPS/chapter1.xhtml","locations":{"fragments":["epubcfi(/6/2!/4/2/1:0)"]}}""",
                3L to """{"href":"OEBPS/chapter1.xhtml","locations":{"progression":0.4}}""",
                4L to synced("0.4231"),
            ),
            requireText = true,
        )

        assertEquals(emptyMap<Long, String>(), placed)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a highlight needs its text but a bookmark does not`() = runTest {
        val position = """{"exact":"","before":"lazy ","after":"dog","progression":0.6}"""
        val locators = mapOf(5L to synced("epubcfi(/6/2!/4/2/1:40)"))

        val asHighlight = placer("[$position]").place("OEBPS/chapter1.xhtml", locators, requireText = true)
        val asBookmark = placer("[$position]").place("OEBPS/chapter1.xhtml", locators, requireText = false)

        assertEquals(emptyMap<Long, String>(), asHighlight)
        assertEquals(0.6, JSONObject(asBookmark.getValue(5L)).getJSONObject("locations").getDouble("progression"))
    }

    @Test
    fun `an item the page cannot resolve is left out and the rest are placed`() = runTest {
        val placed = placer("[null,$found]").place(
            currentHref = "OEBPS/chapter1.xhtml",
            locators = mapOf(
                1L to synced("epubcfi(/6/2!/4/99/1:0)", text = "gone"),
                2L to synced("epubcfi(/6/2!/4/2,/1:16,/1:19)", text = "fox"),
            ),
            requireText = true,
        )

        assertEquals(setOf(2L), placed.keys)
    }

    @Test
    fun `nothing is placed when the chapter on screen is not in the spine`() = runTest {
        val placed = placer("[$found]").place(
            currentHref = "OEBPS/unknown.xhtml",
            locators = mapOf(7L to synced("epubcfi(/6/2!/4/2/1:0)")),
            requireText = false,
        )

        assertEquals(emptyMap<Long, String>(), placed)
    }
}
