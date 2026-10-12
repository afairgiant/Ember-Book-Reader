package com.ember.reader.ui.reader.epub

import android.content.Context
import com.ember.reader.core.readium.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import timber.log.Timber

/**
 * Works out whether a tapped link is a reference to a note and, if so, fetches the note.
 *
 * Few books mark their note references, so this looks at how the link appears in the page
 * (marked as one, or a raised number after some text) and at what it points to (a short
 * block, not a chapter).
 *
 * @param evaluate runs JavaScript in the chapter page and returns the WebView's result.
 * @param readChapter the source of a chapter, given its path in the publication.
 * @param referenceScript `note-reference.js`, a single function expression.
 */
class FootnoteResolver(
    private val evaluate: suspend (script: String) -> String?,
    private val readChapter: suspend (path: String) -> String?,
    private val referenceScript: suspend () -> String,
) {

    /**
     * The HTML of the note a link to [path]#[fragment] refers to, or null when the link is
     * not a note reference and should be followed as usual.
     */
    suspend fun noteFor(path: String, fragment: String): String? {
        if (!isReference(path, fragment)) return null
        val chapter = readChapter(path) ?: return null
        return withContext(Dispatchers.Default) { FootnoteExtractor.noteHtml(chapter, fragment) }
    }

    /** Asks the page whether one of its links to [path]#[fragment] looks like a note reference. */
    private suspend fun isReference(path: String, fragment: String): Boolean {
        val script = "(function(){try{return(" + referenceScript() + ")(" +
            JsonPrimitive(path) + "," + JsonPrimitive(fragment) +
            ")}catch(e){return String(e)}})()"
        return when (val reply = evaluate(script)) {
            "true" -> true
            "false", null -> false
            else -> {
                Timber.w("Footnote: the page script failed: %s", reply)
                false
            }
        }
    }

    companion object {

        fun create(context: Context, navigator: EpubNavigatorFragment, publication: Publication): FootnoteResolver {
            val assets = context.applicationContext.assets
            return FootnoteResolver(
                evaluate = navigator::evaluateJavascript,
                readChapter = { path -> Url(path)?.let { publication.readText(it) } },
                referenceScript = {
                    withContext(Dispatchers.IO) {
                        assets.open("notes/note-reference.js").bufferedReader().use { it.readText() }
                    }
                },
            )
        }
    }
}
