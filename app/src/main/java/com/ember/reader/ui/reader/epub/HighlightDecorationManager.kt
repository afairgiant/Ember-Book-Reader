package com.ember.reader.ui.reader.epub

import android.graphics.RectF
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.readium.toLocator
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
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
            var locator = highlight.locatorJson.toLocator() ?: return@mapNotNull null

            // Synced highlights may have empty href - resolve from CFI spine index
            if (locator.href.toString().isBlank() && publication != null) {
                val resolved = resolveHrefFromCfi(locator, publication)
                if (resolved != null) {
                    locator = resolved
                } else {
                    Timber.w("HighlightDecoration: could not resolve href for highlight %d", highlight.id)
                    return@mapNotNull null
                }
            }

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
     * Resolve the href for a locator that has a CFI fragment but empty href.
     * Extracts the spine index from the CFI and maps it to the publication's reading order.
     */
    private fun resolveHrefFromCfi(locator: org.readium.r2.shared.publication.Locator, publication: Publication): org.readium.r2.shared.publication.Locator? {
        val fragments = locator.locations.fragments
        if (fragments.isEmpty()) return null

        val cfi = fragments.first().removePrefix("epubcfi(").removeSuffix(")")
        // CFI format: /6/N!... where N is the spine position (1-indexed, even numbers)
        val spineMatch = Regex("^/6/(\\d+)").find(cfi) ?: return null
        val spinePosition = spineMatch.groupValues[1].toIntOrNull() ?: return null
        // EPUB CFI uses 1-indexed even numbers: /6/2 = spine[0], /6/4 = spine[1], etc.
        val spineIndex = (spinePosition / 2) - 1

        val readingOrder = publication.readingOrder
        if (spineIndex < 0 || spineIndex >= readingOrder.size) return null

        val link = readingOrder[spineIndex]
        return locator.copy(href = link.url())
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
