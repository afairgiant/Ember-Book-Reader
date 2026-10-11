package com.ember.reader.ui.reader.epub

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CfiScriptTest {

    @Test
    fun `install drops export keywords so the module runs as a plain script`() {
        val foliate = "const helper = 1\nexport const parse = cfi => cfi\nexport const isCFI = /x/\n"

        val script = CfiScript.install(foliate, "window.emberCfi = { parse }")

        assertFalse(script.contains("export "))
        assertTrue(script.contains("const parse = cfi => cfi"))
        assertTrue(script.contains("const helper = 1"))
        assertTrue(script.contains("window.emberCfi = { parse }"))
    }

    @Test
    fun `install only removes export at the start of a line`() {
        val script = CfiScript.install("const text = 'do not export this'\n", "")

        assertTrue(script.contains("'do not export this'"))
    }

    @Test
    fun `install does nothing on a page that already has the script`() {
        assertTrue(CfiScript.install("", "").startsWith("(function(){if(window.emberCfi)return;"))
    }

    /**
     * [CfiScript.install] can only turn the module into a script while every export is a
     * line-leading `export const` and nothing is imported. If an update to the bundled file
     * breaks that, fail here rather than silently producing no CFIs on a device.
     */
    @Test
    fun `the bundled foliate module has only the export shape install can handle`() {
        val lines = File("src/main/assets/cfi/epubcfi.js").readLines()

        val exports = lines.filter { Regex("\\bexport\\b").containsMatchIn(it) }
        assertTrue(exports.isNotEmpty())
        assertEquals(emptyList<String>(), exports.filterNot { it.startsWith("export const ") })
        assertEquals(emptyList<String>(), lines.filter { it.trimStart().startsWith("import ") })
        for (name in listOf("fromRange", "toRange", "parse")) {
            assertTrue(exports.any { it.startsWith("export const $name = ") }, "missing export $name")
        }
    }

    @Test
    fun `call quotes its arguments as JavaScript strings`() {
        val script = CfiScript.call("resolve", "epubcfi(/6/4[it's \"x\"]!/4)")

        assertTrue(script.contains("""window.emberCfi.resolve("epubcfi(/6/4[it's \"x\"]!/4)")"""))
    }

    @Test
    fun `call takes no arguments for a function that needs none`() {
        assertTrue(CfiScript.call("forSelection").contains("window.emberCfi.forSelection()"))
    }
}
