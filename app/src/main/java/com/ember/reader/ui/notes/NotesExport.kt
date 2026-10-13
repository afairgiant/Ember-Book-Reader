package com.ember.reader.ui.notes

import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The forms the notes screen can export in. */
enum class NotesExportFormat(val label: String, val extension: String, val mimeType: String) {
    MARKDOWN("Markdown", "md", "text/markdown"),
    PLAIN_TEXT("Plain text", "txt", "text/plain")
}

/**
 * Writes bookmarks, highlights and notes out as text, book by book in the order given.
 *
 * @param zone the time zone the entries' dates are told in.
 * @param locale how those dates are written.
 */
class NotesExporter(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault()
) {
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    /** [groups] as [format], headed by the day it was exported. */
    fun export(groups: List<NotesBookGroup>, format: NotesExportFormat, exportedOn: LocalDate): String =
        when (format) {
            NotesExportFormat.MARKDOWN -> markdown(groups, exportedOn)
            NotesExportFormat.PLAIN_TEXT -> plainText(groups, exportedOn)
        }

    /**
     * A file name for the export: the book's title when there is one book, and the day
     * either way, with nothing a file system would refuse.
     */
    fun fileName(groups: List<NotesBookGroup>, format: NotesExportFormat, exportedOn: LocalDate): String {
        val subject = groups.singleOrNull()?.book?.title
            ?.replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            ?.trim('-')
            ?.take(MAX_TITLE_IN_FILE_NAME)
            ?.trim('-')
            ?.ifEmpty { null }
        return listOfNotNull("ember-notes", subject, exportedOn.toString()).joinToString("-") + "." + format.extension
    }

    private fun markdown(groups: List<NotesBookGroup>, exportedOn: LocalDate): String = buildString {
        appendLine("# Notes & highlights")
        appendLine()
        appendLine("Exported from Ember on ${dateFormat.format(exportedOn)}.")
        for (group in groups) {
            appendLine()
            appendLine("## ${group.book.title}")
            group.book.author?.let {
                appendLine()
                appendLine("*$it*")
            }
            for (mark in group.marks) {
                appendLine()
                when (mark.kind) {
                    BookMarkKind.BOOKMARK -> appendLine("**Bookmark:** ${mark.text ?: UNTITLED}")
                    BookMarkKind.HIGHLIGHT, BookMarkKind.NOTE -> {
                        (mark.text ?: UNTITLED).lines().forEach { appendLine("> $it".trimEnd()) }
                        mark.note?.let {
                            appendLine()
                            appendLine("**Note:** $it")
                        }
                    }
                }
                appendLine()
                appendLine("*${whereAndWhen(mark)}*")
            }
        }
    }

    private fun plainText(groups: List<NotesBookGroup>, exportedOn: LocalDate): String = buildString {
        appendLine("NOTES & HIGHLIGHTS")
        appendLine("Exported from Ember on ${dateFormat.format(exportedOn)}.")
        for (group in groups) {
            appendLine()
            appendLine()
            appendLine(group.book.title)
            group.book.author?.let { appendLine(it) }
            appendLine("-".repeat(group.book.title.length.coerceIn(MIN_RULE, MAX_RULE)))
            for (mark in group.marks) {
                appendLine()
                when (mark.kind) {
                    BookMarkKind.BOOKMARK -> appendLine("Bookmark: ${mark.text ?: UNTITLED}")
                    BookMarkKind.HIGHLIGHT, BookMarkKind.NOTE -> {
                        appendLine("\"${mark.text ?: UNTITLED}\"")
                        mark.note?.let { appendLine("Note: $it") }
                    }
                }
                appendLine(whereAndWhen(mark))
            }
        }
    }

    private fun whereAndWhen(mark: BookMarkItem): String =
        listOfNotNull(mark.chapter, dateFormat.format(mark.createdAt.atZone(zone))).joinToString(" · ")

    private companion object {
        const val UNTITLED = "Untitled"
        const val MAX_TITLE_IN_FILE_NAME = 40
        const val MIN_RULE = 3
        const val MAX_RULE = 60
    }
}
