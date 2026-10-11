package com.ember.reader.ui.reader.epub

import kotlinx.serialization.json.JsonPrimitive

/** Builds the JavaScript that [FootnoteResolver] runs in a chapter's WebView. */
object FootnoteScript {

    /**
     * A script that evaluates to `true` when a link in the page that points at
     * [targetPath]#[targetFragment] looks like a reference to a note, and to `false`
     * otherwise, including when it throws.
     *
     * @param source `note-reference.js`, a single function expression.
     * @param targetPath the chapter the link points at, as the publication names it.
     */
    fun isReference(source: String, targetPath: String, targetFragment: String): String =
        "(function(){try{return(" + source.trim() + ")(" +
            JsonPrimitive(targetPath) + "," + JsonPrimitive(targetFragment) +
            ")===true}catch(e){return false}})()"
}
