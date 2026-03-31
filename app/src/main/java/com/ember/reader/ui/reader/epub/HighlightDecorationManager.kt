package com.ember.reader.ui.reader.epub

import android.graphics.RectF
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.readium.toLocator
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import timber.log.Timber

/**
 * Manages Readium decorations for text highlights in the EPUB reader.
 * Wraps SelectableNavigator and DecorableNavigator APIs.
 */
class HighlightDecorationManager(
    private val navigator: EpubNavigatorFragment
) {
    companion object {
        const val DECORATION_GROUP = "highlights"
    }

    /**
     * Converts stored Highlights to Readium Decorations and applies them.
     * Readium internally diffs against the previous list for efficiency.
     */
    suspend fun applyHighlights(highlights: List<Highlight>, publication: Publication? = null) {
        val decorations = highlights.mapNotNull { highlight ->
            val locator = highlight.locatorJson.toLocator()
                ?: buildLocatorFromJson(highlight, publication)
                ?: return@mapNotNull null

            Decoration(
                id = highlight.id.toString(),
                locator = locator,
                style = Decoration.Style.Highlight(
                    tint = highlight.color.argb.toInt(),
                    isActive = false
                )
            )
        }
        navigator.applyDecorations(decorations, DECORATION_GROUP)
    }

    /**
     * Build a Locator manually from stored JSON when Readium's fromJSON fails
     * (e.g. synced highlights with empty href). Resolves href from CFI spine index.
     */
    private fun buildLocatorFromJson(highlight: Highlight, publication: Publication?): Locator? {
        if (publication == null) return null
        return runCatching {
            val json = JSONObject(highlight.locatorJson)
            val locations = json.optJSONObject("locations") ?: return null
            val fragments = locations.optJSONArray("fragments") ?: return null
            if (fragments.length() == 0) return null

            val fragment = fragments.getString(0)
            val href = resolveHrefFromFragment(fragment, publication) ?: return null

            val textObj = json.optJSONObject("text")
            val highlightText = textObj?.optString("highlight")?.ifBlank { null }

            Locator(
                href = href,
                mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
                locations = Locator.Locations(fragments = listOf(fragment)),
                text = Locator.Text(highlight = highlightText),
            )
        }.onFailure {
            Timber.w(it, "HighlightDecoration: failed to build locator for highlight %d", highlight.id)
        }.getOrNull()
    }

    /**
     * Resolve an href from a CFI fragment string by extracting the spine index
     * and mapping it to the publication's reading order.
     */
    private fun resolveHrefFromFragment(fragment: String, publication: Publication): Url? {
        // Strip all epubcfi() wrapping
        var cfi = fragment
        while (cfi.startsWith("epubcfi(") && cfi.endsWith(")")) {
            cfi = cfi.removePrefix("epubcfi(").removeSuffix(")")
        }
        // CFI format: /6/N!... where N is the spine position (even numbers)
        val spineMatch = Regex("^/6/(\\d+)").find(cfi) ?: return null
        val spinePosition = spineMatch.groupValues[1].toIntOrNull() ?: return null
        // EPUB CFI: /6/2 = spine[0], /6/4 = spine[1], etc.
        val spineIndex = (spinePosition / 2) - 1

        val readingOrder = publication.readingOrder
        if (spineIndex < 0 || spineIndex >= readingOrder.size) return null
        return readingOrder[spineIndex].url()
    }

    /**
     * Registers a listener for taps on highlight decorations.
     */
    fun addActivationListener(
        onHighlightTapped: (decorationId: String, rect: RectF?) -> Unit
    ): DecorableNavigator.Listener {
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(
                event: DecorableNavigator.OnActivatedEvent
            ): Boolean {
                onHighlightTapped(event.decoration.id, event.rect)
                return true
            }
        }
        navigator.addDecorationListener(DECORATION_GROUP, listener)
        return listener
    }

    fun removeListener(listener: DecorableNavigator.Listener) {
        navigator.removeDecorationListener(listener)
    }

    suspend fun currentSelection(): Selection? = navigator.currentSelection()

    fun clearSelection() = navigator.clearSelection()
}
