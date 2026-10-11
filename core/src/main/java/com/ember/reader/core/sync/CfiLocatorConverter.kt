package com.ember.reader.core.sync

import com.ember.reader.core.epub.EpubCfi
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Converts between Readium Locator JSON (used locally) and EPUB CFI strings (used by Grimmory).
 */
object CfiLocatorConverter {

    /**
     * Extract CFI string from a Readium Locator JSON string.
     * Readium stores CFI in locations.fragments[] array.
     */
    fun extractCfi(locatorJson: String): String? = runCatching {
        val json = JSONObject(locatorJson)
        val locations = json.optJSONObject("locations") ?: return@runCatching null

        // Try fragments array first (Readium 3.x format)
        val fragments = locations.optJSONArray("fragments")
        if (fragments != null && fragments.length() > 0) {
            return@runCatching EpubCfi.unwrap(fragments.getString(0)).ifBlank { null }
        }

        // Fall back to progression-based identifier
        locations.optString("progression").ifBlank { null }
    }.onFailure {
        Timber.w(it, "CfiLocatorConverter: failed to extract CFI from locator")
    }.getOrNull()

    /**
     * Build a minimal Readium Locator JSON from a CFI string and optional metadata.
     * This locator can be used by Readium to navigate to the highlight position.
     */
    fun buildLocatorJson(
        cfi: String,
        selectedText: String? = null,
        chapterTitle: String? = null
    ): String {
        // Normalize: ensure single epubcfi() wrapping
        val normalizedCfi = EpubCfi.wrap(cfi)
        val locations = JSONObject().apply {
            put("fragments", JSONArray().put(normalizedCfi))
        }
        val text = JSONObject().apply {
            if (selectedText != null) put("highlight", selectedText)
        }
        return JSONObject().apply {
            put("href", "")
            put("type", "application/xhtml+xml")
            put("locations", locations)
            if (text.length() > 0) put("text", text)
            if (chapterTitle != null) put("title", chapterTitle)
        }.toString()
    }

    /**
     * The CFI of a locator from [buildLocatorJson] that has not been placed in the book
     * yet: no chapter, and a real CFI to find one with. Null for any other locator.
     */
    fun unplacedCfi(locatorJson: String): String? = runCatching {
        val json = JSONObject(locatorJson)
        if (json.optString("href").isNotEmpty()) return@runCatching null
        val fragment = json.optJSONObject("locations")?.optJSONArray("fragments")?.optString(0)
        fragment?.let { EpubCfi.wrap(it) }?.takeIf { EpubCfi.isRealCfi(it) }
    }.getOrNull()

    /**
     * Rewrites a locator from [buildLocatorJson] once its CFI has been resolved in the
     * book: the chapter, the text around it so the right occurrence is found, and how far
     * into the chapter it is. The CFI stays in `fragments`.
     */
    fun placedLocatorJson(
        locatorJson: String,
        href: String,
        exact: String,
        before: String,
        after: String,
        progression: Double?,
    ): String {
        val json = JSONObject(locatorJson)
        json.put("href", href)
        val locations = json.optJSONObject("locations") ?: JSONObject().also { json.put("locations", it) }
        if (progression != null) locations.put("progression", progression)
        val text = json.optJSONObject("text") ?: JSONObject().also { json.put("text", it) }
        if (exact.isNotEmpty()) text.put("highlight", exact)
        if (before.isNotEmpty()) text.put("before", before)
        if (after.isNotEmpty()) text.put("after", after)
        return json.toString()
    }

    /**
     * Extract chapter title from a Readium Locator JSON string.
     */
    fun extractTitle(locatorJson: String): String? = runCatching {
        JSONObject(locatorJson).optString("title").ifBlank { null }
    }.getOrNull()
}
