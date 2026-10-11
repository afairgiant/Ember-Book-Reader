package com.ember.reader.core.readium

import com.ember.reader.core.epub.EpubSpine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import timber.log.Timber

/**
 * Reads the spine from the publication's own package document. Returns null for anything
 * that is not an EPUB, or when the container or OPF cannot be read.
 */
suspend fun Publication.loadEpubSpine(): EpubSpine? = runCatching {
    val containerXml = readText("META-INF/container.xml") ?: return null
    val opfPath = EpubSpine.rootfilePath(containerXml) ?: return null
    val opfXml = readText(opfPath) ?: return null
    EpubSpine.parse(opfXml, opfPath)
}.onFailure {
    Timber.w(it, "EpubSpine: failed to read the package document")
}.getOrNull()

private suspend fun Publication.readText(path: String): String? =
    Url.fromDecodedPath(path)?.let { readText(it) }

/** The resource at [url] in the publication as UTF-8 text, or null when it cannot be read. */
suspend fun Publication.readText(url: Url): String? = withContext(Dispatchers.IO) {
    val resource = get(url) ?: return@withContext null
    try {
        resource.read().getOrElse { return@withContext null }.toString(Charsets.UTF_8)
    } finally {
        resource.close()
    }
}
