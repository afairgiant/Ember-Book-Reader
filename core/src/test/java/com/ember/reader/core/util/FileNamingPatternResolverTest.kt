package com.ember.reader.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [FileNamingPatternResolver]. Expected values are hand-derived from
 * Grimmory's frontend `pattern-resolver.ts` and `file-mover-component.ts`. If
 * Grimmory updates its resolver, re-derive these expectations from the new TS
 * source — don't just adjust them to make the Kotlin port pass.
 */
class FileNamingPatternResolverTest {

    // === Plain substitution ===

    @Test
    fun `plain field substitution`() {
        assertEquals(
            "Dune.epub",
            FileNamingPatternResolver.resolve("{title}.epub", mapOf("title" to "Dune"))
        )
    }

    @Test
    fun `multiple fields with a separator`() {
        assertEquals(
            "Frank Herbert/Dune",
            FileNamingPatternResolver.resolve(
                "{authors}/{title}",
                mapOf("authors" to "Frank Herbert", "title" to "Dune")
            )
        )
    }

    // === Modifiers ===

    @Test
    fun `first modifier takes first comma-separated item`() {
        assertEquals(
            "Frank Herbert/Dune",
            FileNamingPatternResolver.resolve(
                "{authors:first}/{title}",
                mapOf("authors" to "Frank Herbert, Brian Herbert", "title" to "Dune")
            )
        )
    }

    @Test
    fun `sort modifier swaps last name to front`() {
        assertEquals(
            "Herbert, Frank/Dune",
            FileNamingPatternResolver.resolve(
                "{authors:sort}/{title}",
                mapOf("authors" to "Frank Herbert", "title" to "Dune")
            )
        )
    }

    @Test
    fun `sort modifier with single-word name leaves it unchanged`() {
        assertEquals(
            "Homer/Iliad",
            FileNamingPatternResolver.resolve(
                "{authors:sort}/{title}",
                mapOf("authors" to "Homer", "title" to "Iliad")
            )
        )
    }

    @Test
    fun `initial modifier on authors takes last-name initial`() {
        assertEquals(
            "H/Dune",
            FileNamingPatternResolver.resolve(
                "{authors:initial}/{title}",
                mapOf("authors" to "Frank Herbert", "title" to "Dune")
            )
        )
    }

    @Test
    fun `initial modifier on non-authors field takes first char`() {
        assertEquals(
            "D",
            FileNamingPatternResolver.resolve(
                "{title:initial}",
                mapOf("title" to "Dune")
            )
        )
    }

    @Test
    fun `upper modifier uppercases`() {
        assertEquals(
            "DUNE",
            FileNamingPatternResolver.resolve("{title:upper}", mapOf("title" to "Dune"))
        )
    }

    @Test
    fun `lower modifier lowercases`() {
        assertEquals(
            "dune",
            FileNamingPatternResolver.resolve("{title:lower}", mapOf("title" to "DUNE"))
        )
    }

    @Test
    fun `unknown modifier returns value unchanged`() {
        assertEquals(
            "Dune",
            FileNamingPatternResolver.resolve("{title:bogus}", mapOf("title" to "Dune"))
        )
    }

    // === Optional blocks ===

    @Test
    fun `optional block renders primary when all placeholders present`() {
        assertEquals(
            "Dune.Dune Chronicles",
            FileNamingPatternResolver.resolve(
                "{title}<.{series}>",
                mapOf("title" to "Dune", "series" to "Dune Chronicles")
            )
        )
    }

    @Test
    fun `optional block renders empty when primary value missing and no fallback`() {
        assertEquals(
            "Dune",
            FileNamingPatternResolver.resolve(
                "{title}<.{series}>",
                mapOf("title" to "Dune")
            )
        )
    }

    @Test
    fun `optional block renders fallback when primary value missing`() {
        assertEquals(
            "DuneNO_SERIES",
            FileNamingPatternResolver.resolve(
                "{title}<.{series}|NO_SERIES>",
                mapOf("title" to "Dune")
            )
        )
    }

    @Test
    fun `optional block fallback can reference other fields`() {
        assertEquals(
            "Dune1965",
            FileNamingPatternResolver.resolve(
                "{title}<.{series}|{year}>",
                mapOf("title" to "Dune", "year" to "1965")
            )
        )
    }

    // === Unknown / empty ===

    @Test
    fun `unknown field is empty`() {
        assertEquals("", FileNamingPatternResolver.resolve("{unknown}", emptyMap()))
    }

    @Test
    fun `blank pattern returns empty`() {
        assertEquals("", FileNamingPatternResolver.resolve("", mapOf("title" to "Dune")))
    }

    // === Realistic combined pattern ===

    @Test
    fun `realistic library pattern for series book`() {
        val result = FileNamingPatternResolver.resolve(
            "{authors:sort}/<{series}/>{title}< #{seriesIndex}>",
            mapOf(
                "authors" to "Frank Herbert",
                "series" to "Dune Chronicles",
                "title" to "Dune",
                "seriesIndex" to "01"
            )
        )
        assertEquals("Herbert, Frank/Dune Chronicles/Dune #01", result)
    }

    @Test
    fun `realistic library pattern for standalone book`() {
        val result = FileNamingPatternResolver.resolve(
            "{authors:sort}/<{series}/>{title}< #{seriesIndex}>",
            mapOf(
                "authors" to "Cormac McCarthy",
                "title" to "Blood Meridian"
            )
        )
        assertEquals("McCarthy, Cormac/Blood Meridian", result)
    }

    // === sanitize ===

    @Test
    fun `sanitize strips path-illegal chars`() {
        assertEquals("abc", FileNamingPatternResolver.sanitize("a/b\\c"))
    }

    @Test
    fun `sanitize strips all illegal punctuation`() {
        assertEquals("abcdefgh", FileNamingPatternResolver.sanitize("a/b\\c:d*e?f\"g<h>|"))
    }

    @Test
    fun `sanitize strips control chars`() {
        assertEquals("ab", FileNamingPatternResolver.sanitize("a\u0001b"))
    }

    @Test
    fun `sanitize strips DEL char`() {
        assertEquals("ab", FileNamingPatternResolver.sanitize("a\u007Fb"))
    }

    @Test
    fun `sanitize collapses whitespace`() {
        assertEquals("a b", FileNamingPatternResolver.sanitize("a    b"))
    }

    @Test
    fun `sanitize trims leading and trailing whitespace`() {
        assertEquals("Dune", FileNamingPatternResolver.sanitize("  Dune  "))
    }

    @Test
    fun `sanitize preserves Unicode letters`() {
        assertEquals("Frank Herbért", FileNamingPatternResolver.sanitize("Frank Herbért"))
    }

    @Test
    fun `sanitize of null is empty`() {
        assertEquals("", FileNamingPatternResolver.sanitize(null))
    }

    // === formatYear ===

    @Test
    fun `formatYear extracts year prefix from ISO date`() {
        assertEquals("1965", FileNamingPatternResolver.formatYear("1965-08-01"))
    }

    @Test
    fun `formatYear extracts year prefix from bare year`() {
        assertEquals("1965", FileNamingPatternResolver.formatYear("1965"))
    }

    @Test
    fun `formatYear returns empty for null or blank`() {
        assertEquals("", FileNamingPatternResolver.formatYear(null))
        assertEquals("", FileNamingPatternResolver.formatYear(""))
    }

    // === formatSeriesIndex ===

    @Test
    fun `formatSeriesIndex integer is zero-padded`() {
        assertEquals("01", FileNamingPatternResolver.formatSeriesIndex(1f))
        assertEquals("10", FileNamingPatternResolver.formatSeriesIndex(10f))
    }

    @Test
    fun `formatSeriesIndex decimal keeps fractional part`() {
        assertEquals("01.5", FileNamingPatternResolver.formatSeriesIndex(1.5f))
    }

    @Test
    fun `formatSeriesIndex null is empty`() {
        assertEquals("", FileNamingPatternResolver.formatSeriesIndex(null))
    }
}
