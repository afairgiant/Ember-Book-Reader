package com.ember.reader.ui.reader.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/** Finds the note a link points at and reduces it to HTML that is safe to show. */
object FootnoteExtractor {

    /** Longer than this and the target is a passage of the book, not a note. */
    private const val MAX_NOTE_LENGTH = 4000

    private val NOTE_TYPE = Regex("""\b(footnote|endnote|rearnote|note|doc-footnote|doc-endnote)\b""")
    private val BACKLINK_TYPE = Regex("""\b(backlink|doc-backlink)\b""")

    /** What a link back to the text looks like when it is not marked as one. */
    private val BACKLINK_TEXT = Regex("""^[\s↩↵↑⤴⏎^︎️]+$""")

    private val INLINE = setOf("a", "sup", "sub", "span", "small", "b", "i", "em", "strong")
    private val NOTE_HTML: Safelist = Safelist.basic().removeTags("a")

    /**
     * The note with [id] in a chapter's [documentHtml], or null when there is no such
     * element, it is empty, or it is too long to be a note.
     *
     * The id is often on a small anchor inside the note rather than on the note, so an
     * inline target stands for the block around it.
     */
    fun noteHtml(documentHtml: String, id: String): String? {
        val target = Jsoup.parse(documentHtml).getElementById(id) ?: return null
        val note = target.enclosingNote() ?: target.enclosingBlock() ?: return null
        note.select("a").filter { it.isBacklink() }.forEach { it.remove() }
        val length = note.text().length
        if (length == 0 || length > MAX_NOTE_LENGTH) return null
        return clean(note.html())
    }

    /** Reduces note HTML to basic text formatting: no links, images, styles or scripts. */
    fun clean(html: String): String = Jsoup.clean(html, NOTE_HTML).trim()

    private fun Element.types(): String = attr("epub:type") + " " + attr("role")

    private fun Element.enclosingNote(): Element? =
        generateSequence(this) { it.parent() }
            .takeWhile { it.tagName() != "body" }
            .firstOrNull { it.tagName() == "aside" || NOTE_TYPE.containsMatchIn(it.types()) }

    private fun Element.enclosingBlock(): Element? =
        generateSequence(this) { it.parent() }
            .firstOrNull { it.tagName() !in INLINE }
            ?.takeUnless { it.tagName() == "body" || it.tagName() == "html" }

    private fun Element.isBacklink(): Boolean =
        BACKLINK_TYPE.containsMatchIn(types()) || BACKLINK_TEXT.matches(text())
}
