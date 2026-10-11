package com.ember.reader.core.readium

import com.ember.reader.core.epub.EpubSpine
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import timber.log.Timber

/**
 * A stand-in spine built from Readium's reading order, for when the package document could
 * not be read. A CFI's spine step counts every itemref and the reading order leaves out
 * non-linear ones, so this is right for most books and wrong for the rest.
 */
fun Publication.readingOrderSpine(): EpubSpine = EpubSpine(
    spineStep = 6,
    itemrefs = readingOrder.map {
        EpubSpine.Itemref(idref = "", id = null, linear = true, href = it.url().toString())
    },
)

/**
 * Parses stored locator JSON, including one synced from Grimmory that has not been placed
 * in the book yet: an empty `href` with the CFI in `fragments`. Such a locator is given the
 * chapter its CFI points into, which is enough to open that chapter and to find the
 * highlight's text there.
 */
fun String.toLocator(publication: Publication, spine: EpubSpine?): Locator? {
    toLocator()?.let { return it }
    return runCatching {
        val json = JSONObject(this)
        val fragment = json.optJSONObject("locations")?.optJSONArray("fragments")?.optString(0)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val href = (spine ?: publication.readingOrderSpine()).hrefFor(fragment) ?: return null
        val url = Url(href) ?: return null
        Locator(
            href = publication.linkWithHref(url)?.url() ?: url,
            mediaType = MediaType.XHTML,
            title = json.optString("title").ifBlank { null },
            locations = Locator.Locations(fragments = listOf(fragment)),
            text = Locator.Text(highlight = json.optJSONObject("text")?.optString("highlight")?.ifBlank { null }),
        )
    }.onFailure {
        Timber.w(it, "SyncedLocator: could not build a locator from stored JSON")
    }.getOrNull()
}
