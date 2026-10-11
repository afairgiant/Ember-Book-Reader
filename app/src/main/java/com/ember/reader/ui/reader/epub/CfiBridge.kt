package com.ember.reader.ui.reader.epub

import android.content.Context
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** The text a CFI points at, with up to 64 characters of chapter text on each side. */
data class ResolvedCfi(
    val exact: String,
    val before: String,
    val after: String,
)

/**
 * Computes and resolves real EPUB CFIs by running foliate-js's CFI code in the chapter that
 * Readium has on screen. Grimmory's web reader runs the same code, which is what makes the
 * CFIs interchangeable.
 *
 * Every method returns null when a CFI cannot be produced: the book is fixed-layout, the
 * spine could not be read, the script failed, or the CFI belongs to another chapter.
 * Callers treat null as "no CFI".
 *
 * @param evaluate runs JavaScript in the visible chapter and returns the WebView's result.
 * @param currentHref href of the chapter on screen.
 * @param installScript the script from [CfiScript.install].
 */
class CfiBridge(
    private val evaluate: suspend (script: String) -> String?,
    private val currentHref: () -> String?,
    private val spine: EpubSpine?,
    private val installScript: suspend () -> String,
) {

    /** CFI of the current text selection. Read it before the selection is cleared. */
    suspend fun selectionCfi(): String? = cfiFrom("forSelection")

    /** CFI of the first text position on screen. */
    suspend fun visibleStartCfi(): String? = cfiFrom("forVisibleStart")

    /** The text [cfi] points at. Null unless its chapter is the one on screen. */
    suspend fun resolve(cfi: String): ResolvedCfi? {
        if (!EpubCfi.isRealCfi(cfi)) return null
        val href = currentHref() ?: return null
        val base = spine?.baseCfiFor(href) ?: return null
        if (spine.hrefFor(cfi) != spine.hrefFor(base)) return null
        val result = call("resolve", cfi) as? JsonObject ?: return null
        return ResolvedCfi(
            exact = result.string("exact")?.takeIf { it.isNotEmpty() } ?: return null,
            before = result.string("before").orEmpty(),
            after = result.string("after").orEmpty(),
        )
    }

    private suspend fun cfiFrom(function: String): String? {
        val href = currentHref() ?: return null
        val base = spine?.baseCfiFor(href) ?: return null
        val cfi = (call(function, EpubCfi.wrap(base)) as? JsonPrimitive)?.contentOrNull
        return cfi?.takeIf { EpubCfi.isRealCfi(it) }
    }

    /** Runs one `emberCfi` function, installing the script first if this page lacks it. */
    private suspend fun call(function: String, vararg args: String): JsonElement? {
        val script = CfiScript.call(function, *args)
        var result = CfiScript.decode(evaluate(script))
        if (result is CfiScript.Result.NotInstalled) {
            evaluate(installScript())
            result = CfiScript.decode(evaluate(script))
        }
        return (result as? CfiScript.Result.Value)?.value
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {

        @Volatile
        private var cachedInstallScript: String? = null

        fun create(context: Context, navigator: EpubNavigatorFragment, spine: EpubSpine?): CfiBridge {
            val assets = context.applicationContext.assets
            return CfiBridge(
                evaluate = navigator::evaluateJavascript,
                currentHref = { navigator.currentLocator.value.href.toString() },
                spine = spine,
                installScript = {
                    cachedInstallScript ?: withContext(Dispatchers.IO) {
                        fun asset(name: String) = assets.open("cfi/$name").bufferedReader().use { it.readText() }
                        CfiScript.install(asset("epubcfi.js"), asset("ember-cfi.js"))
                    }.also { cachedInstallScript = it }
                },
            )
        }
    }
}
