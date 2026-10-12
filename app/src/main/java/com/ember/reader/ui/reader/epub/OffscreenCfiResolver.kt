package com.ember.reader.ui.reader.epub

import android.annotation.SuppressLint
import android.content.Context
import android.view.View.MeasureSpec
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ember.reader.core.epub.EpubSpine
import com.ember.reader.core.model.Bookmark
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
import kotlinx.serialization.json.doubleOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import timber.log.Timber

/**
 * Works out real CFIs for highlights and bookmarks that have none, across the whole book,
 * by loading each chapter concerned into a WebView that is never shown and running the
 * same CFI script the reader uses.
 *
 * A highlight is found by its text. One made on this device records its chapter, so only
 * that chapter is searched. One that arrived from another device records just its text;
 * it is placed only if that text occurs exactly once in the book.
 *
 * A bookmark has no text, only its chapter and how far into it the page was. Its CFI is
 * the text that far into the chapter as this WebView lays it out, which is without the
 * book's stylesheets and images and at another size than the page the bookmark was made
 * on. It lands near that page's first line, not reliably on it.
 */
class OffscreenCfiResolver(
    private val context: Context,
    private val publication: Publication,
    private val spine: EpubSpine,
) {

    /** Real CFIs by highlight id. A highlight that cannot be placed is left out. */
    suspend fun cfisFor(highlights: List<Highlight>): Map<Long, String> {
        val passages = highlights.mapNotNull { passageOf(it) }
        if (passages.isEmpty()) return emptyMap()

        return withOffscreenPage { webView, bridge ->
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
        }
    }

    /**
     * Real CFIs by bookmark id. A bookmark is left out when its locator does not say where
     * it is, as for one that came from the server and has not been placed in the book.
     */
    suspend fun cfisForBookmarks(bookmarks: List<Bookmark>): Map<Long, String> {
        val positions = bookmarks.mapNotNull { positionOf(it) }
        if (positions.isEmpty()) return emptyMap()

        return withOffscreenPage { webView, bridge ->
            val placed = mutableMapOf<Long, String>()
            positions.groupBy { it.href }.forEach { (href, here) ->
                if (!webView.loadChapter(href)) return@forEach
                val cfis = bridge.progressionCfis(here.map { it.progression })
                here.zip(cfis).forEach { (position, cfi) -> if (cfi != null) placed[position.id] = cfi }
            }
            placed
        }
    }

    /**
     * Runs [block] with a WebView that is never attached to a window, and the bridge to
     * the page in it. The WebView is given the screen's size, so a page has a layout to
     * measure positions in.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun <T> withOffscreenPage(
        block: suspend (webView: WebView, bridge: CfiBridge) -> T,
    ): T = withContext(Dispatchers.Main) {
        val appContext = context.applicationContext
        val webView = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val screen = resources.displayMetrics
            measure(
                MeasureSpec.makeMeasureSpec(screen.widthPixels, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(screen.heightPixels, MeasureSpec.EXACTLY),
            )
            layout(0, 0, screen.widthPixels, screen.heightPixels)
        }
        try {
            val bridge = CfiBridge(
                evaluate = { script -> webView.evaluate(script) },
                spine = spine,
                installScript = { CfiBridge.installScript(appContext.assets) },
            )
            block(webView, bridge)
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

    private class Position(val id: Long, val href: String, val progression: Double)

    /** The spine chapter a bookmark's locator names and how far into it the bookmark is. */
    private fun positionOf(bookmark: Bookmark): Position? {
        val locator = runCatching { Json.parseToJsonElement(bookmark.locatorJson) as? JsonObject }.getOrNull()
        val href = locator?.string("href")?.takeIf { it.isNotEmpty() }?.let { spine.hrefForPath(it) } ?: return null
        val progression = ((locator["locations"] as? JsonObject)?.get("progression") as? JsonPrimitive)?.doubleOrNull
        return Position(bookmark.id, href, progression ?: return null)
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
