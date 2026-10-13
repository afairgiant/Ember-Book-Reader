package com.ember.reader.ui.reader.audiobook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.model.AudioPosition
import com.ember.reader.core.model.Bookmark

/** A bookmark in an audiobook: where it is, and what it is called. */
data class AudioBookmark(val id: Long, val title: String?, val position: AudioPosition)

/**
 * The audiobook bookmarks among [bookmarks], in the order they come in the book. One that
 * holds no audio position, which an audiobook should not have, is left out.
 */
internal fun audioBookmarksOf(bookmarks: List<Bookmark>): List<AudioBookmark> = bookmarks
    .mapNotNull { bookmark ->
        AudioPosition.fromLocatorJson(bookmark.locatorJson)?.let { AudioBookmark(bookmark.id, bookmark.title, it) }
    }
    .sortedWith(compareBy({ it.position.trackIndex ?: 0 }, { it.position.positionMs }))

/** What a new bookmark is called: the track or chapter and the time, or the time alone. */
internal fun audioBookmarkTitle(place: String?, positionMs: Long): String {
    val time = formatPosition(positionMs)
    return if (place.isNullOrBlank()) time else "$place · $time"
}

/** "12:34", or "1:02:03" from an hour up. */
internal fun formatPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** The book's bookmarks, with a row on top that adds one where playback is now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudiobookBookmarksSheet(
    bookmarks: List<AudioBookmark>,
    currentPositionMs: Long,
    trackTitleOf: (Int) -> String?,
    onAdd: () -> Unit,
    onOpen: (AudioBookmark) -> Unit,
    onDelete: (AudioBookmark) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.audiobook_bookmarks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.audiobook_bookmark_add, formatPosition(currentPositionMs)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (bookmarks.isEmpty()) {
                Text(
                    text = stringResource(R.string.audiobook_bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(bookmark) }
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // One synced from elsewhere may have no name; say where it is.
                            val trackTitle = bookmark.position.trackIndex?.let(trackTitleOf)
                            val place = audioBookmarkTitle(trackTitle, bookmark.position.positionMs)
                            val title = bookmark.title?.takeIf { it.isNotBlank() } ?: place
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // The place is said underneath only when the name does not say it.
                            if (title != place) {
                                Text(
                                    text = place,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.audiobook_bookmark_delete_cd)
                            )
                        }
                    }
                }
            }
        }
    }
}
