package com.ember.reader.ui.reader.epub

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ember.reader.core.epub.EpubSpine
import com.ember.reader.core.model.Highlight
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import timber.log.Timber

/**
 * Works out real CFIs for highlights that are known only by their text, across the whole
 * book, by loading each chapter concerned into a WebView that is never shown and running
 * the same CFI script the reader uses.
 *
 * A highlight made on this device records its chapter, so only that chapter is searched.
 * One that arrived from another device records just its text; it is placed only if that
 * text occurs exactly once in the book.
 */
class OffscreenCfiResolver(
    private val context: Context,
    private val publication: Publication,
    private val spine: EpubSpine,
) {

    /** Real CFIs by highlight id. A highlight that cannot be placed is left out. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun cfisFor(highlights: List<Highlight>): Map<Long, String> = withContext(Dispatchers.Main) {
        val passages = highlights.mapNotNull { passageOf(it) }
        if (passages.isEmpty()) return@withContext emptyMap()

        val webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
        }
        try {
            val assets = context.applicationContext.assets
            val bridge = CfiBridge(
                evaluate = { script -> webView.evaluate(script) },
                spine = spine,
                installScript = { CfiBridge.installScript(assets) },
            )
            val (inChapter, anywhere) = passages.partition { it.href != null }
            val chapters = if (anywhere.isNotEmpty()) {
                spine.itemrefs.mapNotNull { it.href }
            } else {
                inChapter.mapNotNull { it.href }.distinct()
            }

            val placed = mutableMapOf<Long, String>()
            val candidates = mutableMapOf<Long, MutableList<QuoteMatch>>()
            for (href in chapters) {
                val here = inChapter.filter { it.href == href } + anywhere
                if (!webView.loadChapter(href)) continue
                val matches = bridge.quoteCfis(here.map { it.quote })
                here.zip(matches).forEach { (passage, match) ->
                    if (match == null) return@forEach
                    if (passage.href != null) {
                        placed[passage.id] = match.cfi
                    } else {
                        candidates.getOrPut(passage.id) { mutableListOf() } += match
                    }
                }
            }
            candidates.forEach { (id, found) ->
                found.singleOrNull()?.takeIf { it.matches == 1 }?.let { placed[id] = it.cfi }
            }
            placed
        } finally {
            webView.destroy()
        }
    }

    private class Passage(val id: Long, val href: String?, val quote: TextQuote)

    /** The highlight's text and, when its locator has one, the spine chapter it is in. */
    private fun passageOf(highlight: Highlight): Passage? {
        val locator = runCatching { Json.parseToJsonElement(highlight.locatorJson) as? JsonObject }.getOrNull()
        val text = locator?.get("text") as? JsonObject
        val exact = (text?.string("highlight") ?: highlight.selectedText)?.takeIf { it.isNotBlank() } ?: return null
        val href = locator?.string("href")?.takeIf { it.isNotEmpty() }?.let { spine.hrefForPath(it) }
        return Passage(highlight.id, href, TextQuote(exact, text?.string("before"), text?.string("after")))
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /**
     * Loads one chapter with the media type the reader would serve it under, since that
     * decides whether it is parsed as XML or HTML and so what its CFIs are. The address
     * only has to end with the chapter's href, which is how [CfiBridge] identifies it.
     */
    private suspend fun WebView.loadChapter(href: String): Boolean {
        val url = Url(href) ?: return false
        val resource = publication.get(url) ?: return false
        val bytes = withContext(Dispatchers.IO) {
            try {
                resource.read().getOrElse { null }
            } finally {
                resource.close()
            }
        } ?: return false
        val mediaType = publication.linkWithHref(url)?.mediaType?.toString() ?: "application/xhtml+xml"

        val loaded = CompletableDeferred<Unit>()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loaded.complete(Unit)
            }
        }
        loadDataWithBaseURL("$BASE_URL$href", String(bytes, Charsets.UTF_8), mediaType, "UTF-8", null)
        return (withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await() } != null)
            .also { if (!it) Timber.w("OffscreenCfi: %s did not load", href) }
    }

    private suspend fun WebView.evaluate(script: String): String? =
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) { continuation.resume(it) }
        }

    private companion object {
        const val BASE_URL = "https://ember.invalid/publication/"
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
