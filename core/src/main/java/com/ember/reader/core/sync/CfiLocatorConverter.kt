package com.ember.reader.core.sync

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
            val fragment = fragments.getString(0)
            // Strip "epubcfi(" prefix and ")" suffix if present
            return@runCatching fragment
                .removePrefix("epubcfi(")
                .removeSuffix(")")
                .ifBlank { null }
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
        chapterTitle: String? = null,
    ): String {
        val locations = JSONObject().apply {
            put("fragments", JSONArray().put("epubcfi($cfi)"))
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
     * Extract chapter title from a Readium Locator JSON string.
     */
    fun extractTitle(locatorJson: String): String? = runCatching {
        JSONObject(locatorJson).optString("title").ifBlank { null }
    }.getOrNull()
}
