package com.ember.reader.core.model

import org.json.JSONObject

/**
 * The page a PDF bookmark is on, counted from 1, kept inside the bookmark's `locatorJson`.
 *
 * The reader's own locator for a PDF does not say the page plainly (see `PdfPageNavigation`),
 * and a bookmark that came from Grimmory has nothing but its page. So the page is stored
 * under its own key, which the reader's locator parsing passes over.
 */
object PdfBookmarkPage {
    private const val KEY_LOCATIONS = "locations"
    private const val KEY_PAGE = "pdfPage"

    /** The page [locatorJson] holds; null when it is not a PDF bookmark, or one from before pages were kept. */
    fun of(locatorJson: String): Int? = runCatching {
        JSONObject(locatorJson).optJSONObject(KEY_LOCATIONS)?.optInt(KEY_PAGE, 0)?.takeIf { it >= 1 }
    }.getOrNull()

    /** [locatorJson], a locator the reader made, with [page] added to it. */
    fun with(locatorJson: String, page: Int): String = runCatching {
        val json = JSONObject(locatorJson)
        val locations = json.optJSONObject(KEY_LOCATIONS) ?: JSONObject().also { json.put(KEY_LOCATIONS, it) }
        locations.put(KEY_PAGE, page)
        json.toString()
    }.getOrDefault(locatorJson)

    /** The `locatorJson` of a bookmark known only by its [page], as one from Grimmory is. */
    fun locatorJson(page: Int, title: String?): String = JSONObject().apply {
        put("href", "")
        put("type", "application/pdf")
        title?.let { put("title", it) }
        put(KEY_LOCATIONS, JSONObject().put(KEY_PAGE, page))
    }.toString()
}
