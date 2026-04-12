package com.ember.reader.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.ember.reader.ui.common.BookCoverImage
import com.ember.reader.ui.organize.OrganizeFilesSheet
import com.ember.reader.ui.organize.OrganizeFilesViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    onOpenBookDetail: (bookId: String) -> Unit = {},
    onOpenLibrary: (serverId: Long, libraryId: Long) -> Unit = { _, _ -> },
    onEditMetadata: (bookId: String) -> Unit = {},
    metadataSaved: Boolean = false,
    onMetadataSavedConsumed: () -> Unit = {},
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val readStatus by viewModel.readStatus.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val grimmoryDetail by viewModel.grimmoryDetail.collectAsStateWithLifecycle()
    val hardcoverMatch by viewModel.hardcoverMatch.collectAsStateWithLifecycle()
    val hardcoverUserEntry by viewModel.hardcoverUserEntry.collectAsStateWithLifecycle()
    val seriesBooks by viewModel.seriesBooks.collectAsStateWithLifecycle()
    val grimmoryFullBook by viewModel.grimmoryFullBook.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showOrganizeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(metadataSaved) {
        if (metadataSaved) {
            viewModel.refreshFromServer()
            onMetadataSavedConsumed()
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
                actions = {
                    val currentBook = book
                    if (currentBook != null) {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_metadata)) },
                                onClick = {
                                    menuOpen = false
                                    onEditMetadata(currentBook.id)
                                },
                            )
                            val canOrganize = server?.canMoveOrganizeFiles == true
                                && grimmoryDetail != null
                            if (canOrganize) {
                                DropdownMenuItem(
                                    text = { Text("Organize file…") },
                                    onClick = {
                                        menuOpen = false
                                        showOrganizeSheet = true
                                    },
                                )
                            }
                        }
                    }
                    if (currentBook?.isDownloaded == true && currentBook.localPath != null) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            val file = java.io.File(currentBook.localPath)
                            if (file.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_book))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        val currentBook = book
        var coverZoomed by remember { mutableStateOf(false) }
        BackHandler(enabled = coverZoomed) { coverZoomed = false }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        if (currentBook == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero cover — centered
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BookCoverImage(
                        book = currentBook,
                        modifier = Modifier
                            .width(180.dp)
                            .aspectRatio(0.67f)
                            .clip(RoundedCornerShape(12.dp)),
                        onClick = if (currentBook.coverUrl != null) {
                            { coverZoomed = true }
                        } else {
                            null
                        },
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
                val currentServer = server
                val grimmoryBookId = currentBook.grimmoryBookId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentBook.isDownloaded) {
                        Button(
                            onClick = { onOpenReader(currentBook.id, currentBook.format) },
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
                                if (progress?.percentage?.let { it > 0f } == true) stringResource(R.string.continue_listening)
                                else stringResource(R.string.listen)
                            } else {
                                if (progress?.percentage?.let { it > 0f } == true) stringResource(R.string.continue_reading_button)
                                else stringResource(R.string.start_reading)
                            }
                            Text(buttonText)
                        }
                    } else if (currentBook.downloadUrl != null || isAudiobook) {
                        if (isAudiobook && currentBook.grimmoryBookId != null) {
                            Button(
                                onClick = { onOpenReader(currentBook.id, currentBook.format) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
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
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (downloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (downloading) stringResource(R.string.downloading) else stringResource(R.string.download))
                            }
                        }
                    }
                    // View on Grimmory icon button
                    if (currentServer?.isGrimmory == true && grimmoryBookId != null) {
                        val context = LocalContext.current
                        val serverOrigin = com.ember.reader.core.network.serverOrigin(currentServer.url)
                        IconButton(onClick = {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("$serverOrigin/book/$grimmoryBookId"))
                            )
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.view_on_grimmory))
                        }
                    }
                }

                // About card — description + key metadata
                val bookDescription = currentBook.description?.takeIf { it.isNotBlank() }
                    ?: grimmoryDetail?.description?.takeIf { it.isNotBlank() }
                    ?: hardcoverMatch?.description?.takeIf { it.isNotBlank() }
                val gd = grimmoryDetail
                val pageCount = currentBook.pageCount ?: gd?.pageCount ?: hardcoverMatch?.pages
                val published = currentBook.publishedDate ?: gd?.publishedDate
                    ?: hardcoverMatch?.releaseYear?.toString()
                val language = currentBook.language ?: gd?.language

                // About card — collapsible description + key metadata
                if (bookDescription != null || pageCount != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            bookDescription?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                var expanded by remember { mutableStateOf(false) }
                                Text(
                                    text = cleanHtml(it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { expanded = !expanded },
                                )
                                Text(
                                    text = if (expanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                // Book Info card
                val publisher = currentBook.publisher ?: gd?.publisher
                val isbn = gd?.isbn13
                val series = currentBook.series ?: gd?.seriesName
                val seriesIdx = currentBook.seriesIndex ?: gd?.seriesNumber
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Book Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        series?.let {
                            val seriesText = if (seriesIdx != null) {
                                val idx = if (seriesIdx == seriesIdx.toLong().toFloat()) "${seriesIdx.toLong()}" else "$seriesIdx"
                                "$it #$idx"
                            } else it
                            InfoRow(stringResource(R.string.info_series), seriesText)
                        }
                        InfoRow(stringResource(R.string.info_format), currentBook.format.name)
                        pageCount?.let { InfoRow(stringResource(R.string.info_pages), "$it") }
                        language?.let { InfoRow(stringResource(R.string.info_language), it.uppercase()) }
                        published?.let { InfoRow(stringResource(R.string.info_published), it) }
                        publisher?.let { InfoRow(stringResource(R.string.info_publisher), it) }
                        isbn?.let { InfoRow(stringResource(R.string.info_isbn), it) }
                    }
                }

                // Ratings card
                val goodreadsRating = gd?.goodreadsRating
                val personalRating = gd?.personalRating?.takeIf { it > 0 }
                if (goodreadsRating != null || personalRating != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Ratings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            goodreadsRating?.let { rating ->
                                val reviews = gd?.goodreadsReviewCount?.let { " ($it)" } ?: ""
                                InfoRow("Goodreads", "%.1f$reviews".format(rating))
                            }
                            personalRating?.let { InfoRow("Your Rating", "$it / 5") }
                        }
                    }
                }

                // Server Info card
                val subjectList: List<String> = gd?.categories?.toList()
                    ?: currentBook.subjects?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList()
                val primaryFileName = gd?.primaryFile?.fileName
                if (currentServer != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Server", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow(stringResource(R.string.info_server), currentServer.name)
                            gd?.libraryName?.let { libraryName ->
                                val libraryId = gd?.libraryId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .then(
                                            if (libraryId != null) Modifier.clickable {
                                                onOpenLibrary(currentServer.id, libraryId)
                                            } else Modifier
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(R.string.info_library),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = libraryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (libraryId != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            primaryFileName?.let { InfoRow("File", it) }
                            grimmoryFullBook?.metadataMatchScore?.let { score ->
                                MetadataScoreRow(score)
                            }
                            gd?.shelves?.takeIf { it.isNotEmpty() }?.let { shelves ->
                                InfoRow(stringResource(R.string.info_shelves), shelves.mapNotNull { it.name }.joinToString(", "))
                            }
                            if (subjectList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.info_subjects),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    subjectList.forEach { subject ->
                                        Text(
                                            text = subject,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Grimmory card — read status
                if (currentServer?.isGrimmory == true && grimmoryBookId != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.read_status), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                ReadStatus.entries.forEach { status ->
                                    FilterChip(
                                        selected = readStatus == status,
                                        onClick = { viewModel.updateReadStatus(status) },
                                        label = { Text(status.displayName) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Series books
                if (seriesBooks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "More in Series",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(seriesBooks, key = { it.grimmoryBookId }) { item ->
                            SeriesBookCard(
                                item = item,
                                onClick = {
                                    val localId = item.localBookId
                                    if (localId != null) {
                                        onOpenBookDetail(localId)
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        if (coverZoomed && currentBook != null) {
            FullscreenCoverOverlay(
                book = currentBook,
                onDismiss = { coverZoomed = false },
            )
        }
        }
    }

    if (showOrganizeSheet) {
        val currentServer = server
        val currentDetail = grimmoryDetail
        if (currentServer != null && currentDetail != null) {
            val organizeVm = remember(currentDetail.id) {
                viewModel.organizeFilesViewModelFactory.create(
                    baseUrl = currentServer.url,
                    serverId = currentServer.id,
                    bookIds = listOf(currentDetail.id),
                )
            }
            OrganizeFilesSheet(
                viewModel = organizeVm,
                onDismiss = { showOrganizeSheet = false },
                onSuccess = { count, libName ->
                    val plural = if (count == 1) "book" else "books"
                    scope.launch { snackbarHostState.showSnackbar("Moved $count $plural to $libName") }
                    viewModel.refreshFromServer()
                    showOrganizeSheet = false
                },
            )
        } else {
            showOrganizeSheet = false
        }
    }
}

@Composable
private fun SeriesBookCard(item: SeriesBookItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenCoverOverlay(
    book: Book,
    onDismiss: () -> Unit,
) {
    if (book.coverUrl == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        BookCoverImage(
            book = book,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun MetadataScoreRow(score: Float) {
    val pct = score.roundToInt()
    val color = when {
        score >= 90f -> Color(0xFF16A34A) // green
        score >= 70f -> Color(0xFF84CC16) // lime
        score >= 50f -> Color(0xFFF59E0B) // amber
        score >= 30f -> Color(0xFFF97316) // orange
        else -> Color(0xFFEF4444)         // red
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.info_metadata_score),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
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
        ReadStatus.RE_READING -> "Re-reading"
        ReadStatus.READ -> "Read"
        ReadStatus.PARTIALLY_READ -> "Partially read"
        ReadStatus.PAUSED -> "Paused"
        ReadStatus.WONT_READ -> "Won't read"
        ReadStatus.ABANDONED -> "Abandoned"
        ReadStatus.UNSET -> "Unset"
    }
