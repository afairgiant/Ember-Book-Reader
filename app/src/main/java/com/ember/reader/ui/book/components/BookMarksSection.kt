package com.ember.reader.ui.book.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import com.ember.reader.ui.settings.components.SettingsChoiceChips
import com.ember.reader.ui.settings.components.SettingsGroup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val COLLAPSED_COUNT = 5

/** The book's bookmarks, highlights and notes, read-only. Nothing is shown for a book with none. */
@Composable
fun BookMarksSection(marks: List<BookMarkItem>) {
    if (marks.isEmpty()) return
    var filter by rememberSaveable { mutableStateOf<BookMarkKind?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    val summary = BookMarkKind.entries.joinToString(" · ") { kind ->
        val count = marks.count { it.kind == kind }
        val noun = kind.label.lowercase()
        "$count ${if (count == 1) noun.removeSuffix("s") else noun}"
    }
    SettingsGroup(title = "Bookmarks & notes", subtitle = summary) {
        SettingsChoiceChips(
            options = listOf<BookMarkKind?>(null) + BookMarkKind.entries,
            selected = filter,
            onSelect = { filter = it },
            labelOf = { it?.label ?: "All" }
        )
        val matching = marks.filter { filter == null || it.kind == filter }
        if (matching.isEmpty()) {
            Text(
                text = "None in this book yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        val shown = if (showAll) matching else matching.take(COLLAPSED_COUNT)
        shown.forEachIndexed { index, mark ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            BookMarkRow(mark)
        }
        if (matching.size > COLLAPSED_COUNT) {
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(if (showAll) "Show fewer" else "Show all ${matching.size}")
            }
        }
    }
}

/** One bookmark, highlight or note: what it says, its note, and where and when it was made. */
@Composable
internal fun BookMarkRow(mark: BookMarkItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = when (mark.kind) {
                BookMarkKind.BOOKMARK -> Icons.Default.Bookmark
                BookMarkKind.HIGHLIGHT -> Icons.Default.BorderColor
                BookMarkKind.NOTE -> Icons.AutoMirrored.Filled.StickyNote2
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mark.text ?: stringResource(R.string.untitled),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            mark.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = listOfNotNull(
                    mark.chapter,
                    MARK_DATE.format(mark.createdAt.atZone(ZoneId.systemDefault()))
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private val MARK_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
