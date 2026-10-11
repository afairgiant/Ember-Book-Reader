package com.ember.reader.ui.reader.epub

import com.ember.reader.core.epub.EpubSpine
import com.ember.reader.core.sync.CfiLocatorConverter

/**
 * Places highlights and bookmarks that came from Grimmory in the book.
 *
 * A synced item arrives as a CFI and, for a highlight, its text. Until it is placed its
 * locator has no chapter, so a highlight is drawn on the first occurrence of its text and
 * a bookmark cannot be opened. When the chapter it belongs to is on screen, its CFI is
 * resolved there and the locator is rewritten with the chapter, the surrounding text and
 * the position. That happens once per item; the result is stored.
 */
class SyncedPositionPlacer(
    private val bridge: CfiBridge,
    private val spine: EpubSpine,
    /** Readium's href for a chapter, given the href the package document uses. */
    private val readiumHref: (spineHref: String) -> String,
) {

    /**
     * @param currentHref the chapter on screen, to skip items that belong elsewhere.
     * @param locators stored locator JSON by item id.
     * @param requireText true for highlights, which are nothing without their text.
     * @return rewritten locator JSON by id, for the items that could be placed.
     */
    suspend fun place(
        currentHref: String,
        locators: Map<Long, String>,
        requireText: Boolean,
    ): Map<Long, String> {
        val chapter = spine.hrefForPath(currentHref) ?: return emptyMap()
        val unplaced = locators.mapNotNull { (id, json) ->
            val cfi = CfiLocatorConverter.unplacedCfi(json) ?: return@mapNotNull null
            if (spine.hrefFor(cfi) != chapter) return@mapNotNull null
            Triple(id, json, cfi)
        }
        if (unplaced.isEmpty()) return emptyMap()

        val resolved = bridge.resolve(unplaced.map { it.third })
        return unplaced.zip(resolved).mapNotNull { (item, position) ->
            if (position == null || (requireText && position.exact.isBlank())) return@mapNotNull null
            item.first to CfiLocatorConverter.placedLocatorJson(
                locatorJson = item.second,
                href = readiumHref(position.href),
                exact = position.exact,
                before = position.before,
                after = position.after,
                progression = position.progression,
            )
        }.toMap()
    }
}
