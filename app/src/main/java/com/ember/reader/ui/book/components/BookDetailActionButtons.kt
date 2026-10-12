package com.ember.reader.ui.book.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.serverOrigin

/**
 * The book page's actions: one wide button that opens the book, and under it a row with
 * download, "mark finished" and the link to the book on Grimmory, each only when it applies.
 */
@Composable
fun BookDetailActionButtons(
    book: Book,
    server: Server?,
    progress: ReadingProgress?,
    readStatus: ReadStatus?,
    downloading: Boolean,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    onOpenStreamingReader: (bookId: String, format: BookFormat) -> Unit,
    onDownload: () -> Unit,
    onDownloadBlocked: () -> Unit,
    onMarkFinished: () -> Unit,
) {
    val isAudiobook = book.format == BookFormat.AUDIOBOOK
    val grimmoryBookId = book.grimmoryBookId
    val grimmoryServer = server?.takeIf { it.isGrimmory && grimmoryBookId != null }
    val onGrimmory = grimmoryServer != null
    val hasProgress = progress?.percentage?.let { it > 0f } == true
    val available = book.downloadUrl != null || isAudiobook
    val canStream = onGrimmory && (book.format == BookFormat.EPUB || book.format == BookFormat.PDF)
    val showDownload = !book.isDownloaded && book.downloadUrl != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            book.isDownloaded -> PrimaryButton(
                text = stringResource(
                    when {
                        isAudiobook && hasProgress -> R.string.continue_listening
                        isAudiobook -> R.string.listen
                        hasProgress -> R.string.continue_reading_button
                        else -> R.string.start_reading
                    },
                ),
                isAudiobook = isAudiobook,
                onClick = { onOpenReader(book.id, book.format) },
            )
            isAudiobook && grimmoryBookId != null -> PrimaryButton(
                text = stringResource(R.string.listen),
                isAudiobook = true,
                onClick = { onOpenReader(book.id, book.format) },
            )
            available && canStream -> PrimaryButton(
                text = stringResource(R.string.read),
                isAudiobook = false,
                onClick = { onOpenStreamingReader(book.id, book.format) },
            )
        }

        if (showDownload || onGrimmory) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showDownload) {
                    val downloadBlocked = server?.canDownload == false
                    OutlinedButton(
                        onClick = { if (downloadBlocked) onDownloadBlocked() else onDownload() },
                        enabled = !downloading,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = CircleShape,
                    ) {
                        when {
                            downloading ->
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            downloadBlocked ->
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            else ->
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (downloading) R.string.downloading else R.string.download),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (grimmoryServer != null) {
                    val finished = readStatus == ReadStatus.READ
                    OutlinedButton(
                        onClick = onMarkFinished,
                        enabled = !finished,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (finished) "Finished" else "Mark finished", textAlign = TextAlign.Center)
                    }
                    val context = LocalContext.current
                    val origin = serverOrigin(grimmoryServer.url)
                    OutlinedIconButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("$origin/book/$grimmoryBookId")),
                            )
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.view_on_grimmory))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, isAudiobook: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = CircleShape,
    ) {
        Icon(
            if (isAudiobook) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
