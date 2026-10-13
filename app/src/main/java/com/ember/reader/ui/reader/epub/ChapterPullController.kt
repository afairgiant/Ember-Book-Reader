package com.ember.reader.ui.reader.epub

import kotlinx.coroutines.delay
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.util.Url

/**
 * Changes chapter in scroll mode when the reader swipes on past the end of one, or back past
 * its start, so that a book can be read by scrolling alone.
 *
 * The noticing is done by a script in the page ([ChapterPullScript]); this asks it a few
 * times a second, as the page has no way to call out.
 *
 * @param evaluate runs a script in the chapter on screen and returns its result.
 * @param goToNextChapter opens the next chapter at its start; does nothing at the last.
 * @param goToPreviousChapter opens the chapter before at its end; does nothing at the first.
 */
internal class ChapterPullController(
    private val evaluate: suspend (String) -> String?,
    private val goToNextChapter: suspend () -> Unit,
    private val goToPreviousChapter: suspend () -> Unit
) {

    /** Watches for swipes past a chapter's edges until the caller cancels it. */
    suspend fun run() {
        while (true) {
            when (ChapterPullScript.parsePull(evaluate(ChapterPullScript.TAKE))) {
                ChapterPullScript.Pull.NEXT -> goToNextChapter()
                ChapterPullScript.Pull.PREVIOUS -> goToPreviousChapter()
                null -> {}
            }
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 150L
    }
}

/**
 * The end of the chapter before the one at [href], for going back into it the way the
 * reader left it. Null at the first chapter.
 */
internal fun Publication.locatorAtEndOfChapterBefore(href: Url): Locator? {
    val before = readingOrder.getOrNull(readingOrder.indexOfFirstWithHref(href)?.minus(1) ?: return null)
    return before?.let(::locatorFromLink)?.let { it.copy(locations = it.locations.copy(progression = 1.0)) }
}
