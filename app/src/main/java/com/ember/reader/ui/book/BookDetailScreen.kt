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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                text = "${(pct * 100).roundToInt()}% complete",
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
                                "Continue Reading"
                            } else {
                                "Start Reading"
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
                            Text(if (downloading) "Downloading..." else "Download")
                        }
                    }
                }

                // Read status (Grimmory servers only)
                val currentServer = server
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
                                text = "Read Status",
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
                                    text = "Description",
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
                            text = "Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        InfoRow("Format", currentBook.format.name)
                        currentBook.author?.let { InfoRow("Author", it) }
                        currentBook.publisher?.let { InfoRow("Publisher", it) }
                        currentBook.series?.let { InfoRow("Series", it) }
                        currentBook.seriesIndex?.let {
                            val idx = if (it == it.toLong().toFloat()) "${it.toLong()}" else "$it"
                            InfoRow("Volume", idx)
                        }
                        currentBook.language?.let { InfoRow("Language", it.uppercase()) }
                        currentBook.pageCount?.let { InfoRow("Pages", "$it") }
                        currentBook.publishedDate?.let { InfoRow("Published", it) }
                        currentBook.subjects?.let { InfoRow("Subjects", it) }
                        if (currentBook.isDownloaded) {
                            InfoRow("Status", "Downloaded")
                        } else if (currentBook.downloadUrl != null) {
                            InfoRow("Status", "Available on server")
                        } else {
                            InfoRow("Status", "Local file")
                        }
                        currentServer?.let { InfoRow("Server", it.name) }
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
