package com.ember.reader.ui.reader.epub

import com.ember.reader.core.epub.EpubSpine
import com.ember.reader.core.readium.toLocator
import com.ember.reader.core.sync.CfiLocatorConverter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import timber.log.Timber

private const val CHAPTER_LOAD_TIMEOUT_MS = 5_000L
private const val RESOLVE_ATTEMPTS = 5
private const val RESOLVE_RETRY_DELAY_MS = 200L

/**
 * Opens the text a real EPUB CFI points at, such as the position another client pushed.
 *
 * A CFI can only be resolved in its own chapter's page, so this opens that chapter first
 * when another one is on screen, then asks the page where in the chapter the CFI is.
 *
 * @return false when the CFI could not be opened; the reader may then be at the start of
 * the CFI's chapter, and the caller should fall back to another way of finding the position.
 */
suspend fun EpubNavigatorFragment.goToCfi(
    cfi: String,
    publication: Publication,
    spine: EpubSpine?,
    bridge: CfiBridge?,
): Boolean {
    bridge ?: return false
    val chapter = CfiLocatorConverter.buildLocatorJson(cfi).toLocator(publication, spine) ?: return false

    if (currentLocator.value.href != chapter.href) {
        go(chapter)
        withTimeoutOrNull(CHAPTER_LOAD_TIMEOUT_MS) {
            currentLocator.first { it.href == chapter.href }
        } ?: return false
    }

    // The page answers for whichever chapter it holds, so an answer can be missing for a
    // moment while the new chapter replaces the old one.
    repeat(RESOLVE_ATTEMPTS) { attempt ->
        val progression = bridge.resolve(listOf(cfi)).single()?.progression
        if (progression != null) {
            go(chapter.copyWithLocations(fragments = emptyList(), progression = progression))
            Timber.d("CFI navigation: opened %s at %s", cfi, progression)
            return true
        }
        if (attempt < RESOLVE_ATTEMPTS - 1) delay(RESOLVE_RETRY_DELAY_MS)
    }
    Timber.d("CFI navigation: %s did not resolve", cfi)
    return false
}
