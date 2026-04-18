package com.ember.reader.ui.book

import com.ember.reader.core.grimmory.ReadStatus

/** Simple HTML tag stripper for OPDS descriptions. */
internal fun cleanHtml(html: String): String = html.replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .trim()

/**
 * Formats a published date string as MM-DD-YYYY, stripping any time component.
 * Falls back to the original string for non-ISO inputs (e.g. a bare year).
 */
internal fun formatPublishedDate(raw: String): String {
    val dateOnly = raw.substringBefore('T')
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(dateOnly) ?: return dateOnly
    val (year, month, day) = match.destructured
    return "$month-$day-$year"
}

internal val ReadStatus.displayName: String
    get() = when (this) {
        ReadStatus.UNREAD -> "Unread"
        ReadStatus.READING -> "Reading"
        ReadStatus.RE_READING -> "Re-reading"
        ReadStatus.READ -> "Read"
        ReadStatus.PARTIALLY_READ -> "Partially read"
        ReadStatus.PAUSED -> "Paused"
        ReadStatus.WONT_READ -> "Won't read"
        ReadStatus.ABANDONED -> "Abandoned"
        ReadStatus.UNSET -> "Unset"
    }
