package com.ember.reader.ui.reader.epub

import android.graphics.RectF
import com.ember.reader.core.epub.EpubSpine
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
    private val navigator: EpubNavigatorFragment,
    private val publication: Publication,
    spine: EpubSpine?,
) {
    /**
     * A CFI's spine step counts every itemref in the package document. Readium's reading
     * order leaves out non-linear items, so it only stands in when the OPF could not be read.
     */
    private val spine: EpubSpine = spine ?: EpubSpine(
        spineStep = 6,
        itemrefs = publication.readingOrder.map {
            EpubSpine.Itemref(idref = "", id = null, linear = true, href = it.url().toString())
        },
    )

    companion object {
        const val DECORATION_GROUP = "highlights"
    }

    /**
     * Converts stored Highlights to Readium Decorations and applies them.
     * Readium internally diffs against the previous list for efficiency.
     */
    suspend fun applyHighlights(highlights: List<Highlight>) {
        val decorations = highlights.mapNotNull { highlight ->
            val locator = highlight.locatorJson.toLocator()
                ?: buildLocatorFromJson(highlight)
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
     * (e.g. synced highlights with empty href). Resolves href from the CFI's spine step.
     */
    private fun buildLocatorFromJson(highlight: Highlight): Locator? =
        runCatching {
            val json = JSONObject(highlight.locatorJson)
            val locations = json.optJSONObject("locations") ?: return null
            val fragments = locations.optJSONArray("fragments") ?: return null
            if (fragments.length() == 0) return null

            val fragment = fragments.getString(0)
            val href = resolveHrefFromFragment(fragment) ?: return null

            val textObj = json.optJSONObject("text")
            val highlightText = textObj?.optString("highlight")?.ifBlank { null }

            Locator(
                href = href,
                mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
                locations = Locator.Locations(fragments = listOf(fragment)),
                text = Locator.Text(highlight = highlightText)
            )
        }.onFailure {
            Timber.w(it, "HighlightDecoration: failed to build locator for highlight %d", highlight.id)
        }.getOrNull()

    /** Resolve the chapter a CFI fragment string points into. */
    private fun resolveHrefFromFragment(fragment: String): Url? {
        val url = spine.hrefFor(fragment)?.let { Url(it) } ?: return null
        return publication.linkWithHref(url)?.url() ?: url
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
