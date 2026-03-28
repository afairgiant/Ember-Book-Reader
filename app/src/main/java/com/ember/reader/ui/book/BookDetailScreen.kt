package com.ember.reader.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.ember.reader.R
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.common.BookCoverPlaceholderColors
import com.ember.reader.ui.common.bookCoverColorIndex
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val readStatus by viewModel.readStatus.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val coverAuthHeader by viewModel.coverAuthHeader.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        val currentBook = book
        if (currentBook == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cover + basic info header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Cover image
                    BookCover(
                        book = currentBook,
                        coverAuthHeader = coverAuthHeader,
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(0.67f)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Title, author, metadata
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentBook.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        currentBook.author?.let { author ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = author,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        currentBook.series?.let { series ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val idx = currentBook.seriesIndex
                            val seriesText = if (idx != null) {
                                if (idx == idx.toLong().toFloat()) {
                                    "$series #${idx.toLong()}"
                                } else {
                                    "$series #$idx"
                                }
                            } else {
                                series
                            }
                            Text(
                                text = seriesText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Format badge
                        Text(
                            text = currentBook.format.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )

                        // Reading progress
                        val pct = progress?.percentage
                        if (pct != null && pct > 0f) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            Text(
                                text = stringResource(R.string.percent_complete, (pct * 100).roundToInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentBook.isDownloaded) {
                        Button(
                            onClick = { onOpenReader(currentBook.id, currentBook.format) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val buttonText = if (progress?.percentage?.let { it > 0f } == true) {
                                stringResource(R.string.continue_reading_button)
                            } else {
                                stringResource(R.string.start_reading)
                            }
                            Text(buttonText)
                        }
                    } else if (currentBook.downloadUrl != null) {
                        Button(
                            onClick = { viewModel.downloadBook() },
                            enabled = !downloading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (downloading) stringResource(R.string.downloading) else stringResource(R.string.download))
                        }
                    }
                }

                // Share button (downloaded books only)
                if (currentBook.isDownloaded && currentBook.localPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            val file = java.io.File(currentBook.localPath)
                            if (file.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val mimeType = when (currentBook.format) {
                                    BookFormat.PDF -> "application/pdf"
                                    BookFormat.EPUB -> "application/epub+zip"
                                    else -> "*/*"
                                }
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, currentBook.title)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_book))
                    }
                }

                // View on Grimmory button
                val currentServer = server
                val grimmoryBookId = currentBook.grimmoryBookId
                if (currentServer?.isGrimmory == true && grimmoryBookId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val context = LocalContext.current
                    val serverOrigin = com.ember.reader.core.network.serverOrigin(currentServer.url)
                    OutlinedButton(
                        onClick = {
                            val url = "$serverOrigin/book/$grimmoryBookId"
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.view_on_grimmory))
                    }
                }

                // Read status (Grimmory servers only)
                if (currentServer?.isGrimmory == true && currentBook.grimmoryBookId != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.read_status),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ReadStatus.entries.forEach { status ->
                                    FilterChip(
                                        selected = readStatus == status,
                                        onClick = { viewModel.updateReadStatus(status) },
                                        label = { Text(status.displayName) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Description
                currentBook.description?.let { description ->
                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.description_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = cleanHtml(description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Book info card
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.details_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        InfoRow(stringResource(R.string.info_format), currentBook.format.name)
                        currentBook.author?.let { InfoRow(stringResource(R.string.info_author), it) }
                        currentBook.publisher?.let { InfoRow(stringResource(R.string.info_publisher), it) }
                        currentBook.series?.let { InfoRow(stringResource(R.string.info_series), it) }
                        currentBook.seriesIndex?.let {
                            val idx = if (it == it.toLong().toFloat()) "${it.toLong()}" else "$it"
                            InfoRow(stringResource(R.string.info_volume), idx)
                        }
                        currentBook.language?.let { InfoRow(stringResource(R.string.info_language), it.uppercase()) }
                        currentBook.pageCount?.let { InfoRow(stringResource(R.string.info_pages), "$it") }
                        currentBook.publishedDate?.let { InfoRow(stringResource(R.string.info_published), it) }
                        currentBook.subjects?.let { InfoRow(stringResource(R.string.info_subjects), it) }
                        if (currentBook.isDownloaded) {
                            InfoRow(stringResource(R.string.info_status), stringResource(R.string.status_downloaded))
                        } else if (currentBook.downloadUrl != null) {
                            InfoRow(stringResource(R.string.info_status), stringResource(R.string.status_available))
                        } else {
                            InfoRow(stringResource(R.string.info_status), stringResource(R.string.status_local))
                        }
                        currentServer?.let { InfoRow(stringResource(R.string.info_server), it.name) }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BookCover(book: Book, coverAuthHeader: String?, modifier: Modifier = Modifier) {
    if (book.coverUrl != null) {
        val context = LocalContext.current
        val imageModel = remember(book.coverUrl, coverAuthHeader) {
            val url = if (coverAuthHeader?.startsWith("jwt:") == true) {
                val token = coverAuthHeader.removePrefix("jwt:")
                "${book.coverUrl}?token=$token"
            } else {
                book.coverUrl
            }
            ImageRequest.Builder(context)
                .data(url)
                .apply {
                    if (coverAuthHeader != null && !coverAuthHeader.startsWith("jwt:")) {
                        addHeader("Authorization", coverAuthHeader)
                    }
                }
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageModel,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        val colorIndex = bookCoverColorIndex(book.title)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BookCoverPlaceholderColors[colorIndex]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = book.title.take(2).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF5D4037)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Simple HTML tag stripper for OPDS descriptions. */
private fun cleanHtml(html: String): String = html.replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .trim()

private val ReadStatus.displayName: String
    get() = when (this) {
        ReadStatus.UNREAD -> "Unread"
        ReadStatus.READING -> "Reading"
        ReadStatus.READ -> "Read"
        ReadStatus.DNF -> "DNF"
    }
