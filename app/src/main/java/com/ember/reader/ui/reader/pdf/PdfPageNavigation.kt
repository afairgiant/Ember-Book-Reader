package com.ember.reader.ui.reader.pdf

import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

/** The locator that makes the PDF navigator open [page] (counted from 1). */
internal suspend fun Publication.locatorOpeningPdfPage(page: Int): Locator? {
    val positions = positions()
    return positionOpeningPdfPage(page, pageCount = positions.size)?.let { positions.getOrNull(it - 1) }
}

/** The locator that makes the PDF navigator open the page a saved [percentage] (0-1) stands for. */
internal suspend fun Publication.locatorOpeningPdfPercentage(percentage: Float): Locator? {
    val positions = positions()
    val page = pdfPageAt(percentage, pageCount = positions.size)
    return positionOpeningPdfPage(page, pageCount = positions.size)?.let { positions.getOrNull(it - 1) }
}

/**
 * The page (counted from 1) a PDF percentage stands for. The reader saves a PDF's
 * percentage as (page - 1) / (pages - 1); this is that sum the other way round.
 */
internal fun pdfPageAt(percentage: Float, pageCount: Int): Int {
    if (pageCount <= 1) return 1
    return (percentage * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1) + 1
}

/**
 * The position (counted from 1) that makes the navigator open [page] (counted from 1).
 *
 * Readium 3.1.2's Pdfium navigator is one page out in both directions: asked to go to
 * position N it shows page N - 1, and showing page N it reports position N + 1. Its own
 * locators therefore round-trip, but a page number from elsewhere has to be asked for as
 * the position after it. The last page has no such position and cannot be reached this
 * way; the page before it is the closest.
 */
internal fun positionOpeningPdfPage(page: Int, pageCount: Int): Int? =
    if (pageCount < 1) null else (page.coerceAtLeast(1) + 1).coerceAtMost(pageCount)
