package com.ember.reader.ui.reader.epub

import android.graphics.RectF
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.readium.toLocator
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFragment

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
    suspend fun applyHighlights(highlights: List<Highlight>) {
        val decorations = highlights.mapNotNull { highlight ->
            val locator = highlight.locatorJson.toLocator() ?: return@mapNotNull null
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
