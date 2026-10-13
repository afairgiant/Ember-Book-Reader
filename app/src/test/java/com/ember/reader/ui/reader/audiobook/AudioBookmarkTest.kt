package com.ember.reader.ui.reader.audiobook

import com.ember.reader.core.model.AudioPosition
import com.ember.reader.core.model.Bookmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioBookmarkTest {

    private fun bookmark(id: Long, position: AudioPosition) =
        Bookmark(id = id, bookId = "book", locatorJson = position.toLocatorJson(), title = "Bookmark $id")

    @Test
    fun `bookmarks are listed in the order they come in the book`() {
        val listed = audioBookmarksOf(
            listOf(
                bookmark(1, AudioPosition(90_000, 2)),
                bookmark(2, AudioPosition(600_000, 0)),
                bookmark(3, AudioPosition(5_000, 2))
            )
        )

        assertEquals(listOf(2L, 3L, 1L), listed.map { it.id })
    }

    @Test
    fun `a bookmark with no audio position is left out`() {
        val bookBookmark = Bookmark(id = 9, bookId = "book", locatorJson = """{"href":"ch1.xhtml"}""")

        assertEquals(emptyList<AudioBookmark>(), audioBookmarksOf(listOf(bookBookmark)))
    }

    @Test
    fun `a position survives being stored, with or without a track`() {
        assertEquals(AudioPosition(61_500, 3), AudioPosition.fromLocatorJson(AudioPosition(61_500, 3).toLocatorJson()))
        assertEquals(AudioPosition(61_500, null), AudioPosition.fromLocatorJson(AudioPosition(61_500, null).toLocatorJson()))
    }

    @Test
    fun `a new bookmark is named after its place and time`() {
        assertEquals("Chapter 3 \u00B7 12:34", audioBookmarkTitle("Chapter 3", 754_000))
        assertEquals("1:02:03", audioBookmarkTitle(null, 3_723_000))
        assertEquals("0:05", audioBookmarkTitle(" ", 5_999))
    }
}
