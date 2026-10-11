package com.ember.reader.core.epub

/**
 * Helpers for EPUB CFI strings such as `epubcfi(/6/4[chap1]!/4/2/1:10)`.
 *
 * A CFI has a base (the path to a spine `<itemref>` in the package document) and, after
 * the `!`, a content part (the path inside that chapter). Only the base is interpreted
 * here; computing or resolving the content part needs the chapter's DOM.
 */
object EpubCfi {

    private const val PREFIX = "epubcfi("

    /** One `/N[id]` step of a CFI path. */
    data class Step(val index: Int, val id: String? = null)

    /** A CFI split at its indirection step. Neither part is wrapped in `epubcfi(...)`. */
    data class Parts(val base: String, val content: String)

    /** Strips any number of `epubcfi(...)` wrappers. */
    fun unwrap(cfi: String): String {
        var inner = cfi.trim()
        while (inner.startsWith(PREFIX) && inner.endsWith(")")) {
            inner = inner.substring(PREFIX.length, inner.length - 1).trim()
        }
        return inner
    }

    /** Wraps [cfi] in exactly one `epubcfi(...)`. */
    fun wrap(cfi: String): String = "$PREFIX${unwrap(cfi)})"

    /** Splits at the first `!`, or returns null when there is none. */
    fun split(cfi: String): Parts? {
        val inner = unwrap(cfi)
        val bang = indexOfUnescaped(inner, '!') ?: return null
        return Parts(base = inner.substring(0, bang), content = inner.substring(bang + 1))
    }

    /** Joins a base such as `/6/4[chap1]` and a content path into a wrapped CFI. */
    fun join(base: String, content: String): String = "$PREFIX$base!$content)"

    /**
     * Parses the steps of a CFI path that has no `!`, `,` or character offset in it, which
     * is the shape of a base. Returns null if the path is not made of `/N` steps.
     */
    fun steps(path: String): List<Step>? {
        val matches = STEP.findAll(path).toList()
        if (matches.isEmpty() || matches.sumOf { it.value.length } != path.length) return null
        return matches.map { match ->
            Step(
                index = match.groupValues[1].toIntOrNull() ?: return null,
                id = match.groups[2]?.value?.replace(ESCAPE, "$1")?.ifEmpty { null },
            )
        }
    }

    /** Formats one step, escaping the characters CFI reserves inside an `[id]`. */
    fun formatStep(step: Step): String {
        val id = step.id?.let { "[${it.replace(RESERVED) { match -> "^${match.value}" }}]" }.orEmpty()
        return "/${step.index}$id"
    }

    /**
     * True for a CFI that points inside a chapter: wrapped in `epubcfi(...)`, with a base
     * ending on an itemref step and a content path after the `!`.
     *
     * False for the values Ember has used in place of a CFI: the `epubcfi(/6/2)`
     * placeholder (no `!`) and a bare progression number such as `0.4231`.
     */
    fun isRealCfi(cfi: String?): Boolean {
        if (cfi == null) return false
        val trimmed = cfi.trim()
        if (!trimmed.startsWith(PREFIX) || !trimmed.endsWith(")")) return false
        val parts = split(trimmed) ?: return false
        val itemref = steps(parts.base)?.lastOrNull() ?: return false
        return itemref.index >= 2 && itemref.index % 2 == 0 && parts.content.startsWith("/")
    }

    private fun indexOfUnescaped(text: String, target: Char): Int? {
        var inBrackets = false
        var position = 0
        while (position < text.length) {
            val char = text[position]
            when {
                char == '^' -> position++
                char == '[' -> inBrackets = true
                char == ']' -> inBrackets = false
                char == target && !inBrackets -> return position
            }
            position++
        }
        return null
    }

    private val RESERVED = Regex("[\\^\\[\\](),;=]")
    private val STEP = Regex("""/(\d+)(?:\[((?:\^[\s\S]|[^\]^])*)])?""")
    private val ESCAPE = Regex("""\^([\s\S])""")
}
