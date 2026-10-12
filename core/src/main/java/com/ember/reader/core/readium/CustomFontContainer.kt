package com.ember.reader.core.readium

import com.ember.reader.core.fonts.CustomFont
import java.io.File
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.file.FileResource
import org.readium.r2.shared.util.resource.Resource

/**
 * A publication's container with the user's fonts added under
 * [CustomFont.RESOURCE_PREFIX].
 *
 * Readium's navigator only serves fonts bundled in the app's assets. It serves anything in
 * the publication's container, though, from the same origin as the chapters, so a font
 * placed here loads in the reader page like one that shipped inside the book.
 */
internal class CustomFontContainer(
    private val publication: Container<Resource>,
    private val fontDirectory: File,
) : Container<Resource> {

    override val sourceUrl: AbsoluteUrl? get() = publication.sourceUrl

    override val entries: Set<Url> get() = publication.entries

    override fun get(url: Url): Resource? {
        val path = url.path
        if (path == null || !path.startsWith(CustomFont.RESOURCE_PREFIX)) return publication[url]
        val fileName = path.removePrefix(CustomFont.RESOURCE_PREFIX)
        // Only a file directly in the fonts directory, never a path out of it.
        if (fileName.isEmpty() || '/' in fileName || '\\' in fileName || fileName.startsWith(".")) return null
        return File(fontDirectory, fileName).takeIf { it.isFile }?.let { FileResource(it) }
    }

    override fun close() = publication.close()
}
