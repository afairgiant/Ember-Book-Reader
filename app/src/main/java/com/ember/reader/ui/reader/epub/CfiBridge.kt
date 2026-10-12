package com.ember.reader.ui.reader.epub

import android.content.Context
import android.content.res.AssetManager
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import timber.log.Timber

/**
 * What a CFI points at in its chapter.
 *
 * @param href the chapter, as the package document names it.
 * @param exact the text of a range, or empty for a position.
 * @param before up to 64 characters of chapter text before it, and [after] the same after.
 * @param progression how far into the chapter it is, from 0 to 1, when layout could tell.
 */
data class ResolvedCfi(
    val href: String,
    val exact: String,
    val before: String,
    val after: String,
    val progression: Double?,
)

/** A passage known by its text, with the text around it when that is known. */
data class TextQuote(
    val exact: String,
    val before: String? = null,
    val after: String? = null,
)

/** Where a [TextQuote] was found. [matches] is how often its text occurs in that chapter. */
data class QuoteMatch(
    val cfi: String,
    val matches: Int,
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

    /**
     * What each of [cfis] points at. The answer has one entry per CFI, null for one that is
     * not real, does not resolve, or belongs to a chapter other than the one in the page.
     */
    suspend fun resolve(cfis: List<String>): List<ResolvedCfi?> {
        val none = List<ResolvedCfi?>(cfis.size) { null }
        if (cfis.none { EpubCfi.isRealCfi(it) }) return none
        val reply = call("resolve", JsonArray(cfis.map { JsonPrimitive(it) }).toString()) ?: return none
        val answers = reply.value as? JsonArray ?: return none
        return cfis.mapIndexed { index, cfi ->
            val answer = answers.getOrNull(index) as? JsonObject ?: return@mapIndexed null
            if (!EpubCfi.isRealCfi(cfi) || spine.hrefFor(cfi) != reply.href) return@mapIndexed null
            ResolvedCfi(
                href = reply.href,
                exact = answer.string("exact").orEmpty(),
                before = answer.string("before").orEmpty(),
                after = answer.string("after").orEmpty(),
                progression = (answer["progression"] as? JsonPrimitive)?.doubleOrNull,
            )
        }
    }

    /**
     * CFIs for passages known only by their text, searched for in the page's chapter. The
     * answer has one entry per quote, null where the chapter does not contain it.
     */
    suspend fun quoteCfis(quotes: List<TextQuote>): List<QuoteMatch?> {
        val none = List<QuoteMatch?>(quotes.size) { null }
        if (quotes.isEmpty()) return none
        val json = buildJsonArray {
            quotes.forEach { quote ->
                addJsonObject {
                    put("exact", quote.exact)
                    put("before", quote.before)
                    put("after", quote.after)
                }
            }
        }.toString()
        val reply = call("forQuotes", json) ?: return none
        val base = spine.baseCfiFor(reply.href) ?: return none
        val answers = reply.value as? JsonArray ?: return none
        return quotes.indices.map { index ->
            val answer = answers.getOrNull(index) as? JsonObject ?: return@map null
            val cfi = answer.string("cfi")?.let { EpubCfi.join(base, EpubCfi.unwrap(it)) }
            if (!EpubCfi.isRealCfi(cfi)) return@map null
            QuoteMatch(cfi = cfi!!, matches = (answer["matches"] as? JsonPrimitive)?.intOrNull ?: 1)
        }
    }

    /**
     * CFIs of the first text at each of [progressions] (0 to 1) through the page's chapter.
     * The answer has one entry per progression, null where the chapter gave none. See
     * `forProgressions` in `ember-cfi.js` for how exact they are.
     */
    suspend fun progressionCfis(progressions: List<Double>): List<String?> {
        val none = List<String?>(progressions.size) { null }
        if (progressions.isEmpty()) return none
        val reply = call("forProgressions", JsonArray(progressions.map { JsonPrimitive(it) }).toString())
            ?: return none
        val base = spine.baseCfiFor(reply.href) ?: return none
        val answers = reply.value as? JsonArray ?: return none
        return progressions.indices.map { index ->
            (answers.getOrNull(index) as? JsonPrimitive)?.contentOrNull
                ?.let { EpubCfi.join(base, EpubCfi.unwrap(it)) }
                ?.takeIf { EpubCfi.isRealCfi(it) }
        }
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

        suspend fun installScript(assets: AssetManager): String =
            cachedInstallScript ?: withContext(Dispatchers.IO) {
                fun asset(name: String) = assets.open("cfi/$name").bufferedReader().use { it.readText() }
                CfiScript.install(asset("epubcfi.js"), asset("ember-cfi.js"))
            }.also { cachedInstallScript = it }
    }
}
