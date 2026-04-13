package com.ember.reader.core.sync

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CfiLocatorConverterTest {

    @Nested
    inner class ExtractCfi {

        @Test
        fun `extracts CFI from fragments array with epubcfi prefix`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
                put("locations", JSONObject().apply {
                    put("fragments", org.json.JSONArray().put("epubcfi(/6/4!/4/2)"))
                })
            }.toString()

            assertEquals("/6/4!/4/2", CfiLocatorConverter.extractCfi(json))
        }

        @Test
        fun `extracts raw fragment without epubcfi prefix`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
                put("locations", JSONObject().apply {
                    put("fragments", org.json.JSONArray().put("/6/4!/4/2"))
                })
            }.toString()

            assertEquals("/6/4!/4/2", CfiLocatorConverter.extractCfi(json))
        }

        @Test
        fun `falls back to progression when no fragments`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
                put("locations", JSONObject().apply {
                    put("progression", "0.5")
                })
            }.toString()

            assertEquals("0.5", CfiLocatorConverter.extractCfi(json))
        }

        @Test
        fun `returns null for empty fragments array`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
                put("locations", JSONObject().apply {
                    put("fragments", org.json.JSONArray())
                })
            }.toString()

            assertNull(CfiLocatorConverter.extractCfi(json))
        }

        @Test
        fun `returns null for invalid JSON`() {
            assertNull(CfiLocatorConverter.extractCfi("not json"))
        }

        @Test
        fun `returns null when no locations object`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
            }.toString()

            assertNull(CfiLocatorConverter.extractCfi(json))
        }

        @Test
        fun `returns null for blank fragment`() {
            val json = JSONObject().apply {
                put("locations", JSONObject().apply {
                    put("fragments", org.json.JSONArray().put("epubcfi()"))
                })
            }.toString()

            // "epubcfi()" strips to "" which is blank -> null
            assertNull(CfiLocatorConverter.extractCfi(json))
        }
    }

    @Nested
    inner class BuildLocatorJson {

        @Test
        fun `wraps CFI in epubcfi() in fragments array`() {
            val result = CfiLocatorConverter.buildLocatorJson("/6/4")
            val json = JSONObject(result)

            val fragments = json.getJSONObject("locations").getJSONArray("fragments")
            assertEquals(1, fragments.length())
            assertEquals("epubcfi(/6/4)", fragments.getString(0))
        }

        @Test
        fun `does not double-wrap already prefixed CFI`() {
            val result = CfiLocatorConverter.buildLocatorJson("epubcfi(/6/4)")
            val json = JSONObject(result)

            val fragment = json.getJSONObject("locations").getJSONArray("fragments").getString(0)
            assertEquals("epubcfi(/6/4)", fragment)
            // Verify no "epubcfi(epubcfi(...))"
            assertTrue(!fragment.contains("epubcfi(epubcfi("))
        }

        @Test
        fun `includes selectedText as highlight in text object`() {
            val result = CfiLocatorConverter.buildLocatorJson("/6/4", selectedText = "hello world")
            val json = JSONObject(result)

            assertEquals("hello world", json.getJSONObject("text").getString("highlight"))
        }

        @Test
        fun `includes chapterTitle as title field`() {
            val result = CfiLocatorConverter.buildLocatorJson("/6/4", chapterTitle = "Chapter 1")
            val json = JSONObject(result)

            assertEquals("Chapter 1", json.getString("title"))
        }

        @Test
        fun `omits text object when no selectedText`() {
            val result = CfiLocatorConverter.buildLocatorJson("/6/4")
            val json = JSONObject(result)

            assertTrue(!json.has("text"))
        }

        @Test
        fun `sets href and type fields`() {
            val result = CfiLocatorConverter.buildLocatorJson("/6/4")
            val json = JSONObject(result)

            assertEquals("", json.getString("href"))
            assertEquals("application/xhtml+xml", json.getString("type"))
        }
    }

    @Nested
    inner class ExtractTitle {

        @Test
        fun `extracts title from locator JSON`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
                put("title", "Chapter 1")
            }.toString()

            assertEquals("Chapter 1", CfiLocatorConverter.extractTitle(json))
        }

        @Test
        fun `returns null when no title field`() {
            val json = JSONObject().apply {
                put("href", "/chapter1.xhtml")
            }.toString()

            assertNull(CfiLocatorConverter.extractTitle(json))
        }

        @Test
        fun `returns null for blank title`() {
            val json = JSONObject().apply {
                put("title", "")
            }.toString()

            assertNull(CfiLocatorConverter.extractTitle(json))
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        fun `buildLocatorJson then extractCfi returns original CFI`() {
            val originalCfi = "/6/4!/4/2/1:0"
            val locatorJson = CfiLocatorConverter.buildLocatorJson(originalCfi)
            val extractedCfi = CfiLocatorConverter.extractCfi(locatorJson)

            assertNotNull(extractedCfi)
            assertEquals(originalCfi, extractedCfi)
        }

        @Test
        fun `buildLocatorJson then extractTitle returns original title`() {
            val locatorJson = CfiLocatorConverter.buildLocatorJson(
                "/6/4",
                chapterTitle = "My Chapter",
            )
            assertEquals("My Chapter", CfiLocatorConverter.extractTitle(locatorJson))
        }
    }
}
