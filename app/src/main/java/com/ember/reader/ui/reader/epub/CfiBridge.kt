package com.ember.reader.ui.reader.epub

import android.content.Context
import android.content.res.AssetManager
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import timber.log.Timber

/** The text a CFI points at, with up to 64 characters of chapter text on each side. */
data class ResolvedCfi(
    val exact: String,
    val before: String,
    val after: String,
)

/**
 * Computes and resolves real EPUB CFIs by running foliate-js's CFI code in a chapter page.
 * Grimmory's web reader runs the same code, which is what makes the CFIs interchangeable.
 *
 * The page reports its own URL with every answer, and the spine step is taken from that,
 * so a CFI can never pair one chapter's spine step with another chapter's content.
 *
 * Every method returns null when a CFI cannot be produced: the book is fixed-layout, the
 * script failed, or the CFI belongs to another chapter. Callers treat null as "no CFI".
 *
 * @param evaluate runs JavaScript in the chapter page and returns the WebView's result.
 * @param installScript the script from [CfiScript.install].
 */
class CfiBridge(
    private val evaluate: suspend (script: String) -> String?,
    private val spine: EpubSpine,
    private val installScript: suspend () -> String,
) {

    /** CFI of the current text selection. Read it before the selection is cleared. */
    suspend fun selectionCfi(): String? = cfiFrom("forSelection")

    /** CFI of the first text position on screen. */
    suspend fun visibleStartCfi(): String? = cfiFrom("forVisibleStart")

    /** The text [cfi] points at. Null unless its chapter is the one in the page. */
    suspend fun resolve(cfi: String): ResolvedCfi? {
        if (!EpubCfi.isRealCfi(cfi)) return null
        val reply = call("resolve", cfi) ?: return null
        if (spine.hrefFor(cfi) != reply.href) return null
        val text = reply.value as? JsonObject ?: return null
        return ResolvedCfi(
            exact = text.string("exact")?.takeIf { it.isNotEmpty() } ?: return null,
            before = text.string("before").orEmpty(),
            after = text.string("after").orEmpty(),
        )
    }

    private suspend fun cfiFrom(function: String): String? {
        val reply = call(function) ?: return null
        val content = (reply.value as? JsonPrimitive)?.contentOrNull ?: return null
        val base = spine.baseCfiFor(reply.href) ?: return null
        return EpubCfi.join(base, EpubCfi.unwrap(content)).takeIf { EpubCfi.isRealCfi(it) }
    }

    /** What one `emberCfi` function returned, and the chapter it ran in. */
    private class Reply(val href: String, val value: JsonElement)

    /** Runs one `emberCfi` function, installing the script first if the page lacks it. */
    private suspend fun call(function: String, vararg args: String): Reply? {
        val script = CfiScript.call(function, *args)
        var result = parse(evaluate(script))
        if (result == NOT_INSTALLED) {
            result = parse(evaluate(installScript() + script))
            if (result !is JsonObject) Timber.w("CfiBridge: the CFI script did not install")
        }
        val reply = result as? JsonObject ?: return null
        reply.string("error")?.let {
            Timber.w("CfiBridge: %s failed: %s", function, it)
            return null
        }
        return Reply(
            href = reply.string("path")?.let { spine.hrefForPath(it) } ?: return null,
            value = reply["value"]?.takeUnless { it is JsonNull } ?: return null,
        )
    }

    private fun parse(raw: String?): JsonElement? =
        runCatching { raw?.let { Json.parseToJsonElement(it) } }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {

        private val NOT_INSTALLED = JsonPrimitive(false)

        @Volatile
        private var cachedInstallScript: String? = null

        fun create(context: Context, navigator: EpubNavigatorFragment, spine: EpubSpine): CfiBridge {
            val assets = context.applicationContext.assets
            return CfiBridge(
                evaluate = navigator::evaluateJavascript,
                spine = spine,
                installScript = { installScript(assets) },
            )
        }

        private suspend fun installScript(assets: AssetManager): String =
            cachedInstallScript ?: withContext(Dispatchers.IO) {
                fun asset(name: String) = assets.open("cfi/$name").bufferedReader().use { it.readText() }
                CfiScript.install(asset("epubcfi.js"), asset("ember-cfi.js"))
            }.also { cachedInstallScript = it }
    }
}
