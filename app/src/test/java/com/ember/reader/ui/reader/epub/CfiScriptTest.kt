package com.ember.reader.ui.reader.epub

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    @Test
    fun `call quotes its arguments as JavaScript strings`() {
        val script = CfiScript.call("resolve", "epubcfi(/6/4[it's \"x\"]!/4)")

        assertTrue(script.contains("""window.emberCfi.resolve("epubcfi(/6/4[it's \"x\"]!/4)")"""))
    }

    @Test
    fun `decode reads a string result`() {
        // evaluateJavascript JSON-encodes the script's return value, which is itself JSON.
        val raw = JsonPrimitive(JsonPrimitive("epubcfi(/6/4!/4/2/1:0)").toString()).toString()

        assertEquals(
            CfiScript.Result.Value(JsonPrimitive("epubcfi(/6/4!/4/2/1:0)")),
            CfiScript.decode(raw),
        )
    }

    @Test
    fun `decode reads an object result`() {
        val raw = JsonPrimitive("""{"exact":"fox","before":"brown ","after":" jumps"}""").toString()

        val value = (CfiScript.decode(raw) as CfiScript.Result.Value).value as JsonObject

        assertEquals(JsonPrimitive("fox"), value["exact"])
    }

    @Test
    fun `decode reports a page without the script`() {
        val script = CfiScript.call("forSelection", "epubcfi(/6/4)")
        val marker = Regex("return '([^']+)'").find(script)!!.groupValues[1]

        assertEquals(CfiScript.Result.NotInstalled, CfiScript.decode(JsonPrimitive(marker).toString()))
    }

    @Test
    fun `decode treats null, a thrown error and garbage as no value`() {
        val none = CfiScript.Result.Value(null)

        assertEquals(none, CfiScript.decode(null))
        assertEquals(none, CfiScript.decode("null"))
        assertEquals(none, CfiScript.decode(JsonPrimitive("null").toString()))
        assertEquals(none, CfiScript.decode("not json"))
    }
}
