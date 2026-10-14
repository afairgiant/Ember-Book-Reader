package com.ember.reader.ui.reader.audiobook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
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
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat

/** A book counts as finished, for choosing what plays next, from this much of it heard. */
internal const val FINISHED_PROGRESS = 0.95f

/** The audiobook that plays when the current one ends, and why it was chosen. */
data class NextAudiobook(val book: Book, val fromQueue: Boolean)

/**
 * What plays after [current]: the first audiobook in the Up Next [queue], or with nothing
 * queued, the next unfinished audiobook of [current]'s series from [seriesMates]. A book
 * that cannot be played, having neither a file on the device nor a server, is passed over.
 */
internal fun nextAudiobook(
    current: Book,
    queue: List<Book>,
    seriesMates: List<Book>,
    progressOf: (bookId: String) -> Float?
): NextAudiobook? {
    queue.firstOrNull { it.id != current.id && it.isPlayableAudiobook }
        ?.let { return NextAudiobook(it, fromQueue = true) }
    val series = current.series ?: return null
    val index = current.seriesIndex ?: return null
    return seriesMates
        .filter { it.id != current.id && it.isPlayableAudiobook && it.series == series }
        .filter { (it.seriesIndex ?: Float.NEGATIVE_INFINITY) > index }
        .filter { (progressOf(it.id) ?: 0f) < FINISHED_PROGRESS }
        .minByOrNull { it.seriesIndex ?: Float.MAX_VALUE }
        ?.let { NextAudiobook(it, fromQueue = false) }
}

/** An audiobook the player can start: one on the device, or one a server can stream. */
private val Book.isPlayableAudiobook: Boolean
    get() = format == BookFormat.AUDIOBOOK && (isDownloaded || serverId != null)

/** [queue] with [bookId] moved one place towards the front; unchanged when it is first or absent. */
internal fun movedUp(queue: List<String>, bookId: String): List<String> {
    val index = queue.indexOf(bookId)
    if (index <= 0) return queue
    return queue.toMutableList().apply {
        removeAt(index)
        add(index - 1, bookId)
    }
}

/** The audiobooks queued to play after the current one, with what will in fact play next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpNextSheet(
    queue: List<Book>,
    next: NextAudiobook?,
    onPlayNow: (Book) -> Unit,
    onMoveUp: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.up_next_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = when {
                    next == null -> stringResource(R.string.up_next_nothing)
                    next.fromQueue -> stringResource(R.string.up_next_then, next.book.title)
                    else -> stringResource(R.string.up_next_then_series, next.book.title)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (queue.isEmpty()) {
                Text(
                    text = stringResource(R.string.up_next_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            LazyColumn {
                itemsIndexed(queue, key = { _, book -> book.id }) { index, book ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayNow(book) }
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            book.author?.takeIf { it.isNotBlank() }?.let { author ->
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index > 0) {
                            IconButton(onClick = { onMoveUp(book) }) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(R.string.up_next_move_up_cd)
                                )
                            }
                        }
                        IconButton(onClick = { onRemove(book) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.up_next_remove_cd))
                        }
                    }
                }
            }
        }
    }
}
