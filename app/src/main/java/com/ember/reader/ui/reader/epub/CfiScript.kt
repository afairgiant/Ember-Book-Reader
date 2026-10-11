package com.ember.reader.ui.reader.epub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the JavaScript that [CfiBridge] runs in a chapter's WebView, and decodes what
 * comes back.
 */
object CfiScript {

    /** Returned by [call] when the page does not have `window.emberCfi` yet. */
    private const val NOT_INSTALLED = "ember-cfi-not-installed"

    private val EXPORT = Regex("(?m)^export ")

    /**
     * A script that defines `window.emberCfi` once per page.
     *
     * [foliateSource] is foliate-js's `epubcfi.js`, an ES module. A WebView cannot import a
     * module from a string, so its `export` keywords are dropped and it shares one function
     * scope with [wrapperSource] (`ember-cfi.js`), which then sees its exports as locals.
     */
    fun install(foliateSource: String, wrapperSource: String): String =
        "(function(){if(window.emberCfi)return;\n" +
            foliateSource.replace(EXPORT, "") +
            "\n" + wrapperSource +
            "\n})();"

    /** A script that calls `window.emberCfi.<function>(args)` and returns its result as JSON. */
    fun call(function: String, vararg args: String): String {
        val arguments = args.joinToString(",") { JsonPrimitive(it).toString() }
        return "(function(){try{" +
            "if(!window.emberCfi)return '$NOT_INSTALLED';" +
            "return JSON.stringify(window.emberCfi.$function($arguments)??null);" +
            "}catch(e){return null}})()"
    }

    sealed interface Result {
        /** The page needs [install] to run first. */
        data object NotInstalled : Result

        /** The call ran. [value] is null when it had nothing to return or threw. */
        data class Value(val value: JsonElement?) : Result
    }

    /**
     * Decodes what `evaluateJavascript` hands back for a [call] script: the script's own
     * return value, JSON-encoded once more by the WebView.
     */
    fun decode(raw: String?): Result {
        val returned = runCatching { raw?.let { Json.parseToJsonElement(it) } }.getOrNull()
        if (returned !is JsonPrimitive || !returned.isString) return Result.Value(null)
        if (returned.content == NOT_INSTALLED) return Result.NotInstalled
        val value = runCatching { Json.parseToJsonElement(returned.content) }.getOrNull()
        return Result.Value(value.takeUnless { it is JsonNull })
    }
}
