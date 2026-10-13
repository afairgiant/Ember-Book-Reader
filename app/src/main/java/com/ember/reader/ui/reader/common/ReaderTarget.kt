package com.ember.reader.ui.reader.common

/** A place the reader is asked to open at, in place of where the book was left. */
sealed interface ReaderTarget {

    /** One of this device's bookmarks, by its id. */
    data class Bookmark(val id: Long) : ReaderTarget

    /** One of this device's highlights, by its id. */
    data class Highlight(val id: Long) : ReaderTarget

    /** A place known only by its EPUB CFI: something Grimmory holds and this device does not. */
    data class Cfi(val cfi: String) : ReaderTarget

    /** The value of the reader route's `mark` argument; null for a [Cfi], which has its own. */
    val markArgument: String?
        get() = when (this) {
            is Bookmark -> "$BOOKMARK_PREFIX$id"
            is Highlight -> "$HIGHLIGHT_PREFIX$id"
            is Cfi -> null
        }

    companion object {
        private const val BOOKMARK_PREFIX = "bookmark-"
        private const val HIGHLIGHT_PREFIX = "highlight-"

        /** The target the reader route's `mark` and `cfi` arguments name, if they name one. */
        fun fromArguments(mark: String?, cfi: String?): ReaderTarget? {
            if (!cfi.isNullOrBlank()) return Cfi(cfi)
            mark ?: return null
            return when {
                mark.startsWith(BOOKMARK_PREFIX) ->
                    mark.removePrefix(BOOKMARK_PREFIX).toLongOrNull()?.let(::Bookmark)
                mark.startsWith(HIGHLIGHT_PREFIX) ->
                    mark.removePrefix(HIGHLIGHT_PREFIX).toLongOrNull()?.let(::Highlight)
                else -> null
            }
        }
    }
}
