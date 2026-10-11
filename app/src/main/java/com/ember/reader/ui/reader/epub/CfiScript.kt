package com.ember.reader.ui.reader.epub

import kotlinx.serialization.json.JsonPrimitive

/** Builds the JavaScript that [CfiBridge] runs in a chapter's WebView. */
object CfiScript {

    private val EXPORT = Regex("(?m)^export ")

    /**
     * A script that defines `window.emberCfi` once per page.
     *
     * [foliateSource] is foliate-js's `epubcfi.js`, an ES module. A WebView cannot import a
     * module from a string, so its `export` keywords are dropped and it shares one function
     * scope with [wrapperSource] (`ember-cfi.js`), which then sees its exports as locals.
     * This only works while every export is a line-leading `export const` and the module
     * imports nothing; `CfiScriptTest` checks the bundled file for that.
     */
    fun install(foliateSource: String, wrapperSource: String): String =
        "(function(){if(window.emberCfi)return;\n" +
            foliateSource.replace(EXPORT, "") +
            "\n" + wrapperSource +
            "\n})();"

    /**
     * A script that calls `window.emberCfi.<function>(args)`. It evaluates to one of:
     * - `false` when the page does not have the script from [install] yet
     * - `{path, value}`: the page's URL path and the function's result (null for none)
     * - `{error}` when the function threw
     */
    fun call(function: String, vararg args: String): String {
        val arguments = args.joinToString(",") { JsonPrimitive(it).toString() }
        return "(function(){try{" +
            "if(!window.emberCfi)return false;" +
            "return{path:location.pathname,value:window.emberCfi.$function($arguments)??null};" +
            "}catch(e){return{error:String(e)}}})()"
    }
}
