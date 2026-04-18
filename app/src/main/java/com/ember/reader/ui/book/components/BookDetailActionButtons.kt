package com.ember.reader.ui.book.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.serverOrigin

@Composable
fun BookDetailActionButtons(
    book: Book,
    server: Server?,
    progress: ReadingProgress?,
    downloading: Boolean,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    onOpenStreamingReader: (bookId: String, format: BookFormat) -> Unit,
    onDownload: () -> Unit,
    onDownloadBlocked: () -> Unit,
) {
    val isAudiobook = book.format == BookFormat.AUDIOBOOK
    val grimmoryBookId = book.grimmoryBookId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (book.isDownloaded) {
            Button(
                onClick = { onOpenReader(book.id, book.format) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    if (isAudiobook) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val buttonText = if (isAudiobook) {
                    if (progress?.percentage?.let { it > 0f } == true) {
                        stringResource(R.string.continue_listening)
                    } else {
                        stringResource(R.string.listen)
                    }
                } else {
                    if (progress?.percentage?.let { it > 0f } == true) {
                        stringResource(R.string.continue_reading_button)
                    } else {
                        stringResource(R.string.start_reading)
                    }
                }
                Text(buttonText)
            }
        } else if (book.downloadUrl != null || isAudiobook) {
            val canStream = server?.isGrimmory == true &&
                grimmoryBookId != null &&
                (book.format == BookFormat.EPUB || book.format == BookFormat.PDF)
            val downloadBlocked = server?.canDownload == false

            if (isAudiobook && book.grimmoryBookId != null) {
                Button(
                    onClick = { onOpenReader(book.id, book.format) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.listen))
                }
            }
            if (canStream) {
                Button(
                    onClick = { onOpenStreamingReader(book.id, book.format) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.read_streaming))
                }
            }
            if (book.downloadUrl != null) {
                OutlinedButton(
                    onClick = {
                        if (downloadBlocked) onDownloadBlocked() else onDownload()
                    },
                    enabled = !downloading,
                    modifier = if ((isAudiobook && book.grimmoryBookId != null) || canStream) {
                        Modifier
                    } else {
                        Modifier.weight(1f)
                    },
                    shape = RoundedCornerShape(12.dp),
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
                        text = when {
                            downloading -> stringResource(R.string.downloading)
                            else -> stringResource(R.string.download)
                        },
                    )
                }
            }
        }
        if (server?.isGrimmory == true && grimmoryBookId != null) {
            val context = LocalContext.current
            val origin = serverOrigin(server.url)
            IconButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("$origin/book/$grimmoryBookId")),
                )
            }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.view_on_grimmory))
            }
        }
    }
}
