package com.ember.reader.core.readium

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens a real EPUB with Readium to check that the package document is reachable through
 * the publication's container, and that the spine keeps what Readium's reading order drops.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class EpubSpineLoaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `loadEpubSpine reads every itemref while the reading order skips the non-linear one`() = runBlocking {
        val publication = open(writeEpub())

        val spine = publication.loadEpubSpine()

        assertNotNull(spine)
        assertEquals(
            listOf("OEBPS/cover.xhtml", "OEBPS/chapter1.xhtml", "OEBPS/chapter2.xhtml"),
            spine!!.itemrefs.map { it.href },
        )
        assertEquals(
            listOf("OEBPS/chapter1.xhtml", "OEBPS/chapter2.xhtml"),
            publication.readingOrder.map { it.url().toString() },
        )
        // /6/4 is chapter1 in the package document; readingOrder[4 / 2 - 1] would be chapter2.
        assertEquals("OEBPS/chapter1.xhtml", spine.hrefFor("epubcfi(/6/4!/4/2)"))
        assertEquals("/6/4[ref1]", spine.baseCfiFor(publication.readingOrder.first().url().toString()))
        publication.close()
    }

    private suspend fun open(file: File): Publication {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(DefaultPublicationParser(context, httpClient, assetRetriever, null))
        val asset = assetRetriever.retrieve(file).getOrElse { error("retrieve failed: $it") }
        return opener.open(asset, allowUserInteraction = false).getOrElse { error("open failed: $it") }
    }

    private fun writeEpub(): File {
        val file = temporaryFolder.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                },
            )
            zip.write(mimetype)
            zip.closeEntry()
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add("META-INF/container.xml", CONTAINER)
            add("OEBPS/content.opf", OPF)
            add("OEBPS/nav.xhtml", page("Contents"))
            add("OEBPS/cover.xhtml", page("Cover"))
            add("OEBPS/chapter1.xhtml", page("One"))
            add("OEBPS/chapter2.xhtml", page("Two"))
        }
        return file
    }

    private fun page(title: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head><body><p>$title</p></body></html>"""

    private companion object {
        const val CONTAINER = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        const val OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">urn:uuid:00000000-0000-0000-0000-000000000001</dc:identifier>
    <dc:title>Spine Test</dc:title>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="cover" linear="no"/>
    <itemref idref="c1" id="ref1"/>
    <itemref idref="c2"/>
  </spine>
</package>"""
    }
}
