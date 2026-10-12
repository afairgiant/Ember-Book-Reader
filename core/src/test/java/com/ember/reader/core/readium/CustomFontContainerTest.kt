package com.ember.reader.core.readium

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.file.FileResource
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CustomFontContainerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val chapterUrl = Url("text/chapter1.xhtml")!!

    private fun container(): Pair<CustomFontContainer, Resource> {
        val fonts = temporaryFolder.newFolder("fonts")
        File(fonts, "Literata.ttf").writeBytes(byteArrayOf(0, 1, 0, 0, 42))
        File(temporaryFolder.root, "secret.txt").writeText("not a font")
        val chapter: Resource = FileResource(temporaryFolder.newFile("chapter1.xhtml"))
        val publication = object : Container<Resource> {
            override val entries: Set<Url> = setOf(chapterUrl)
            override fun get(url: Url): Resource? = chapter.takeIf { url == chapterUrl }
            override fun close() = Unit
        }
        return CustomFontContainer(publication, fonts) to chapter
    }

    @Test
    fun `a font under the prefix is served from the fonts directory`() = runBlocking {
        val (container, _) = container()

        val bytes = container[Url("ember-fonts/Literata.ttf")!!]!!.read().getOrElse { error("unreadable") }

        assertArrayEquals(byteArrayOf(0, 1, 0, 0, 42), bytes)
    }

    @Test
    fun `everything else comes from the publication`() {
        val (container, chapter) = container()

        assertSame(chapter, container[chapterUrl])
        assertNull(container[Url("text/missing.xhtml")!!])
        assertEquals(setOf(chapterUrl), container.entries)
    }

    @Test
    fun `a font that does not exist is not served`() {
        val (container, _) = container()

        assertNull(container[Url("ember-fonts/Missing.ttf")!!])
    }

    @Test
    fun `a path out of the fonts directory is not served`() {
        val (container, _) = container()

        assertNull(container[Url("ember-fonts/../secret.txt")!!])
        assertNull(container[Url("ember-fonts/%2E%2E/secret.txt")!!])
        assertNull(container[Url("ember-fonts/sub/Literata.ttf")!!])
    }
}
