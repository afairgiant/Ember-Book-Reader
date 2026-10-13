package com.ember.reader.core.epub

import java.io.StringReader
import java.net.URLDecoder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * The spine of an EPUB exactly as its package document (OPF) lists it.
 *
 * The base of an EPUB CFI is a path to an `<itemref>` in the OPF, counted over every
 * itemref. Readium's `readingOrder` leaves out `linear="no"` items, so its indexes drift
 * from CFI spine steps as soon as a book has one; this class keeps the OPF's own order.
 *
 * @param spineStep CFI step of `<spine>` within `<package>`: 6 in a conventional OPF.
 */
data class EpubSpine(
    val spineStep: Int,
    val itemrefs: List<Itemref>,
) {

    /**
     * @param href container-relative URL of the manifest item, or null when the itemref
     * points at an id the manifest does not have.
     */
    data class Itemref(
        val idref: String,
        val id: String?,
        val linear: Boolean,
        val href: String?,
    )

    /** The CFI base for the chapter at [href], such as `/6/4[chap1]`. Not wrapped. */
    fun baseCfiFor(href: String): String? {
        val wanted = comparable(href)
        val position = itemrefs.indexOfFirst { it.href != null && comparable(it.href) == wanted }
        if (position < 0) return null
        val itemref = EpubCfi.Step(index = (position + 1) * 2, id = itemrefs[position].id)
        return EpubCfi.formatStep(EpubCfi.Step(spineStep)) + EpubCfi.formatStep(itemref)
    }

    /**
     * The chapter a CFI points into. [cfi] may be a full CFI or only its base.
     *
     * Resolves by the itemref's `[id]` when the CFI carries one and the spine has it,
     * otherwise by position, which is how foliate-js (Grimmory's web reader) does it.
     */
    fun hrefFor(cfi: String): String? {
        val base = EpubCfi.split(cfi)?.base ?: EpubCfi.unwrap(cfi)
        val step = EpubCfi.steps(base)?.lastOrNull() ?: return null
        step.id?.let { id ->
            itemrefs.firstOrNull { it.id == id }?.let { return it.href }
        }
        if (step.index < 2 || step.index % 2 != 0) return null
        return itemrefs.getOrNull(step.index / 2 - 1)?.href
    }

    /**
     * The chapter a served URL path belongs to: the one whose href the path ends with,
     * preferring the longest. A renderer serves `OEBPS/ch1.xhtml` under some prefix of its
     * own, such as `/publication/OEBPS/ch1.xhtml`.
     */
    fun hrefForPath(path: String): String? {
        val served = comparable(path)
        return itemrefs
            .mapNotNull { it.href }
            .filter { href -> comparable(href).let { served == it || served.endsWith("/$it") } }
            .maxByOrNull { it.length }
    }

    companion object {
        /** Some EPUBs start their XML with one, which the parser rejects. Written as an escape so the source has none. */
        private const val BYTE_ORDER_MARK = "\uFEFF"


        private val parserFactory: XmlPullParserFactory by lazy {
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        }

        /** Reads the package document path from `META-INF/container.xml`. */
        fun rootfilePath(containerXml: String): String? = runCatching {
            val parser = createParser(containerXml)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    parser.getAttributeValue(null, "full-path")?.takeIf { it.isNotBlank() }?.let { return it }
                }
                event = parser.next()
            }
            null
        }.getOrNull()

        /**
         * Parses a package document.
         *
         * @param opfPath container-relative path of the OPF; manifest hrefs resolve against it.
         */
        fun parse(opfXml: String, opfPath: String): EpubSpine? = runCatching {
            val parser = createParser(opfXml)
            val manifest = mutableMapOf<String, String>()
            val itemrefs = mutableListOf<Itemref>()
            var spineStep: Int? = null
            var packageChildren = 0
            var section: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.depth) {
                        2 -> {
                            packageChildren++
                            section = parser.name
                            if (section == "spine") spineStep = packageChildren * 2
                        }
                        3 -> when {
                            section == "manifest" && parser.name == "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                if (id != null && href != null) manifest[id] = href
                            }
                            // Counted even without an idref: its position still takes a CFI step.
                            section == "spine" && parser.name == "itemref" -> itemrefs += Itemref(
                                idref = parser.getAttributeValue(null, "idref").orEmpty(),
                                id = parser.getAttributeValue(null, "id")?.takeIf { it.isNotEmpty() },
                                linear = parser.getAttributeValue(null, "linear") != "no",
                                href = null,
                            )
                        }
                    }
                }
                event = parser.next()
            }
            EpubSpine(
                spineStep = spineStep ?: return null,
                itemrefs = itemrefs.map { it.copy(href = manifest[it.idref]?.let { href -> resolve(opfPath, href) }) },
            )
        }.getOrNull()

        /** Resolves a manifest href against the OPF's directory, collapsing `.` and `..`. */
        private fun resolve(opfPath: String, href: String): String {
            if (href.startsWith("/") || SCHEME.containsMatchIn(href)) return href
            val segments = opfPath.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
            for (segment in href.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> segments.removeLastOrNull()
                    else -> segments += segment
                }
            }
            return segments.joinToString("/")
        }

        /** Form used to compare hrefs: no fragment or query, percent-decoded. */
        private fun comparable(href: String): String {
            val path = href.substringBefore('#').substringBefore('?')
            // URLDecoder turns '+' into a space, which is only right in a query string.
            return runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)
        }

        private fun createParser(xml: String): XmlPullParser =
            parserFactory.newPullParser().apply { setInput(StringReader(xml.removePrefix(BYTE_ORDER_MARK))) }

        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    }
}
