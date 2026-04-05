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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
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
import com.ember.reader.core.hardcover.HardcoverBookDetail
import com.ember.reader.core.hardcover.HardcoverStatus
import com.ember.reader.core.hardcover.HardcoverUserBookEntry
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
    val grimmoryDetail by viewModel.grimmoryDetail.collectAsStateWithLifecycle()
    val hardcoverMatch by viewModel.hardcoverMatch.collectAsStateWithLifecycle()
    val hardcoverUserEntry by viewModel.hardcoverUserEntry.collectAsStateWithLifecycle()
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
                // Hero cover — centered
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BookCover(
                        book = currentBook,
                        coverAuthHeader = coverAuthHeader,
                        modifier = Modifier
                            .width(180.dp)
                            .aspectRatio(0.67f),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title + Author + Series — centered
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = currentBook.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    currentBook.author?.let { author ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }

                    currentBook.series?.let { series ->
                        Spacer(modifier = Modifier.height(4.dp))
                        val idx = currentBook.seriesIndex
                        val seriesText = if (idx != null) {
                            if (idx == idx.toLong().toFloat()) "$series #${idx.toLong()}" else "$series #$idx"
                        } else {
                            series
                        }
                        Text(
                            text = seriesText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Hardcover community rating
                    val hcMatch = hardcoverMatch
                    if (hcMatch != null) {
                        val avg = hcMatch.averageRating
                        if (avg != null && avg > 0f) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HardcoverRatingRow(rating = avg, count = hcMatch.ratingsCount)
                        }
                    }

                    // Hardcover user status badge
                    val hcEntry = hardcoverUserEntry
                    if (hcEntry != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hardcover: ${HardcoverStatus.label(hcEntry.statusId)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }

                    // Format badge + reading progress
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentBook.format.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        val pct = progress?.percentage
                        if (pct != null && pct > 0f) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${(pct * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val pct = progress?.percentage
                    if (pct != null && pct > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                val isAudiobook = currentBook.format == BookFormat.AUDIOBOOK
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
                                if (isAudiobook) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val buttonText = if (isAudiobook) {
                                if (progress?.percentage?.let { it > 0f } == true) stringResource(R.string.continue_listening)
                                else stringResource(R.string.listen)
                            } else {
                                if (progress?.percentage?.let { it > 0f } == true) stringResource(R.string.continue_reading_button)
                                else stringResource(R.string.start_reading)
                            }
                            Text(buttonText)
                        }
                    } else if (currentBook.downloadUrl != null || isAudiobook) {
                        // For audiobooks from Grimmory: show Listen (streaming) + Download
                        if (isAudiobook && currentBook.grimmoryBookId != null) {
                            Button(
                                onClick = { onOpenReader(currentBook.id, currentBook.format) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.listen))
                            }
                        }
                        if (currentBook.downloadUrl != null) {
                            OutlinedButton(
                                onClick = { viewModel.downloadBook() },
                                enabled = !downloading,
                                modifier = if (isAudiobook && currentBook.grimmoryBookId != null) Modifier else Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (downloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (downloading) stringResource(R.string.downloading) else stringResource(R.string.download))
                            }
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

                // Description — prefer local, fall back to Grimmory API
                val bookDescription = currentBook.description?.takeIf { it.isNotBlank() }
                    ?: grimmoryDetail?.description?.takeIf { it.isNotBlank() }
                bookDescription?.let { description ->
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

                // Book info card (merged from local + Grimmory API)
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

                        // Merge local book data with Grimmory API detail
                        val gd = grimmoryDetail

                        InfoRow(stringResource(R.string.info_format), currentBook.format.name)
                        val authors = gd?.authors?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                            ?: currentBook.author
                        authors?.let { InfoRow(stringResource(R.string.info_author), it) }

                        gd?.subtitle?.let { InfoRow(stringResource(R.string.info_subtitle), it) }

                        val publisher = currentBook.publisher ?: gd?.publisher
                        publisher?.let { InfoRow(stringResource(R.string.info_publisher), it) }

                        val series = currentBook.series ?: gd?.seriesName
                        series?.let { InfoRow(stringResource(R.string.info_series), it) }
                        val seriesIdx = currentBook.seriesIndex ?: gd?.seriesNumber
                        seriesIdx?.let {
                            val idx = if (it == it.toLong().toFloat()) "${it.toLong()}" else "$it"
                            InfoRow(stringResource(R.string.info_volume), idx)
                        }

                        val language = currentBook.language ?: gd?.language
                        language?.let { InfoRow(stringResource(R.string.info_language), it.uppercase()) }

                        val pageCount = currentBook.pageCount ?: gd?.pageCount
                        pageCount?.let { InfoRow(stringResource(R.string.info_pages), "$it") }

                        val published = currentBook.publishedDate ?: gd?.publishedDate
                        published?.let { InfoRow(stringResource(R.string.info_published), it) }

                        gd?.isbn13?.let { InfoRow(stringResource(R.string.info_isbn), it) }

                        val subjects = currentBook.subjects
                            ?: gd?.categories?.joinToString(", ")
                        subjects?.let { InfoRow(stringResource(R.string.info_subjects), it) }

                        gd?.personalRating?.let {
                            if (it > 0) InfoRow(stringResource(R.string.info_rating), "$it / 5")
                        }

                        gd?.goodreadsRating?.let { rating ->
                            val reviews = gd.goodreadsReviewCount?.let { " ($it reviews)" } ?: ""
                            InfoRow(stringResource(R.string.info_goodreads), "%.1f$reviews".format(rating))
                        }

                        gd?.libraryName?.let { InfoRow(stringResource(R.string.info_library), it) }

                        gd?.shelves?.takeIf { it.isNotEmpty() }?.let { shelves ->
                            InfoRow(stringResource(R.string.info_shelves), shelves.mapNotNull { it.name }.joinToString(", "))
                        }

                        gd?.fileTypes?.takeIf { it.isNotEmpty() }?.let {
                            InfoRow(stringResource(R.string.info_file_types), it.joinToString(", "))
                        }

                        gd?.addedOn?.let {
                            InfoRow(stringResource(R.string.info_added), it.substringBefore("T"))
                        }

                        gd?.lastReadTime?.let {
                            InfoRow(stringResource(R.string.info_last_read), it.substringBefore("T"))
                        }

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

@Composable
private fun HardcoverRatingRow(rating: Float, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RatingStars(rating = rating)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.1f".format(rating),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = " ($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RatingStars(rating: Float) {
    Row {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Default.Star
                rating >= i - 0.5f -> Icons.Default.StarHalf
                else -> Icons.Default.StarBorder
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFFB300))
        }
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
