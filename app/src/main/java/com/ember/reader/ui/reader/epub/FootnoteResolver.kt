package com.ember.reader.ui.reader.epub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import timber.log.Timber

/**
 * Works out whether a tapped link is a reference to a note and, if so, fetches the note.
 *
 * Readium does this itself for links marked `epub:type="noteref"`. Most books do not mark
 * them, so this looks at how the link appears in the page (a raised number after some
 * text) and at what it points to (a short block, not a chapter).
 *
 * @param evaluate runs JavaScript in the chapter page and returns the WebView's result.
 * @param readChapter the source of a chapter, given its path in the publication.
 * @param referenceScript `note-reference.js`.
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
        val script = FootnoteScript.isReference(referenceScript(), path, fragment)
        if (evaluate(script) != "true") return null
        val chapter = readChapter(path) ?: return null
        return withContext(Dispatchers.Default) { FootnoteExtractor.noteHtml(chapter, fragment) }
    }

    companion object {

        @Volatile
        private var cachedScript: String? = null

        fun create(context: Context, navigator: EpubNavigatorFragment, publication: Publication): FootnoteResolver {
            val assets = context.applicationContext.assets
            return FootnoteResolver(
                evaluate = navigator::evaluateJavascript,
                readChapter = { path -> publication.readChapter(path) },
                referenceScript = {
                    cachedScript ?: withContext(Dispatchers.IO) {
                        assets.open("notes/note-reference.js").bufferedReader().use { it.readText() }
                    }.also { cachedScript = it }
                },
            )
        }

        private suspend fun Publication.readChapter(path: String): String? = runCatching {
            val resource = Url(path)?.let { get(it) } ?: return null
            try {
                resource.read().getOrElse { return null }.toString(Charsets.UTF_8)
            } finally {
                resource.close()
            }
        }.onFailure {
            Timber.w(it, "Footnote: could not read %s", path)
        }.getOrNull()
    }
}
