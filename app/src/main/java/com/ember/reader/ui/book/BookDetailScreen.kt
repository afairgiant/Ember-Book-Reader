package com.ember.reader.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.book.components.BookAboutCard
import com.ember.reader.ui.book.components.BookDetailActionButtons
import com.ember.reader.ui.book.components.BookDetailHero
import com.ember.reader.ui.book.components.BookInfoCard
import com.ember.reader.ui.book.components.BookRatingsCard
import com.ember.reader.ui.book.components.BookReadStatusCard
import com.ember.reader.ui.book.components.BookSeriesCarousel
import com.ember.reader.ui.book.components.BookServerInfoCard
import com.ember.reader.ui.book.components.FullscreenCoverOverlay
import com.ember.reader.ui.organize.OrganizeFilesSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    onOpenStreamingReader: (bookId: String, format: BookFormat) -> Unit = { _, _ -> },
    onOpenBookDetail: (bookId: String) -> Unit = {},
    onOpenLibrary: (serverId: Long, libraryId: Long) -> Unit = { _, _ -> },
    onEditMetadata: (bookId: String) -> Unit = {},
    metadataSaved: Boolean = false,
    onMetadataSavedConsumed: () -> Unit = {},
    viewModel: BookDetailViewModel = hiltViewModel(),
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
    val sourceAppearance by viewModel.sourceAppearance.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
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
                            val canOrganize = server?.canMoveOrganizeFiles == true &&
                                grimmoryDetail != null
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
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
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
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_book))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
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
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshFromServer() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        BookDetailHero(
                            book = currentBook,
                            hardcoverUserEntry = hardcoverUserEntry,
                            progress = progress,
                            sourceAppearance = sourceAppearance,
                            onCoverClick = { coverZoomed = true },
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        BookDetailActionButtons(
                            book = currentBook,
                            server = server,
                            progress = progress,
                            downloading = downloading,
                            onOpenReader = onOpenReader,
                            onOpenStreamingReader = onOpenStreamingReader,
                            onDownload = { viewModel.downloadBook() },
                            onDownloadBlocked = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Your account doesn't have download permission on this server",
                                    )
                                }
                            },
                        )

                        val gd = grimmoryDetail
                        val bookDescription = currentBook.description?.takeIf { it.isNotBlank() }
                            ?: gd?.description?.takeIf { it.isNotBlank() }
                            ?: hardcoverMatch?.description?.takeIf { it.isNotBlank() }
                        val pageCount = currentBook.pageCount ?: gd?.pageCount ?: hardcoverMatch?.pages
                        val published = (
                            currentBook.publishedDate ?: gd?.publishedDate
                                ?: hardcoverMatch?.releaseYear?.toString()
                            )?.let(::formatPublishedDate)
                        val language = currentBook.language ?: gd?.language

                        if (bookDescription != null || pageCount != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            BookAboutCard(description = bookDescription)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        BookInfoCard(
                            format = currentBook.format.name,
                            series = currentBook.series ?: gd?.seriesName,
                            seriesIndex = currentBook.seriesIndex ?: gd?.seriesNumber,
                            pageCount = pageCount,
                            language = language,
                            published = published,
                            publisher = currentBook.publisher ?: gd?.publisher,
                            isbn = gd?.isbn13,
                        )

                        val fullMeta = grimmoryFullBook?.metadata
                        if (gd != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            BookRatingsCard(
                                personalRating = gd.personalRating,
                                goodreadsRating = fullMeta?.goodreadsRating,
                                goodreadsReviewCount = fullMeta?.goodreadsReviewCount,
                                amazonRating = fullMeta?.amazonRating,
                                amazonReviewCount = fullMeta?.amazonReviewCount,
                                hardcoverRating = fullMeta?.hardcoverRating,
                                hardcoverReviewCount = fullMeta?.hardcoverReviewCount,
                                onRatingChange = { viewModel.setPersonalRating(it) },
                            )
                        }

                        val currentServer = server
                        if (currentServer != null) {
                            val subjectList: List<String> =
                                grimmoryFullBook?.metadata?.categoryNames?.toList()
                                    ?: gd?.categories?.toList()
                                    ?: currentBook.subjects?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                                    ?: emptyList()
                            Spacer(modifier = Modifier.height(12.dp))
                            BookServerInfoCard(
                                server = currentServer,
                                grimmoryDetail = gd,
                                metadataScore = grimmoryFullBook?.metadataMatchScore,
                                subjects = subjectList,
                                primaryFileName = gd?.primaryFile?.fileName,
                                onOpenLibrary = onOpenLibrary,
                            )
                        }

                        if (currentServer?.isGrimmory == true && currentBook.grimmoryBookId != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            BookReadStatusCard(
                                currentStatus = readStatus,
                                onStatusChange = { viewModel.updateReadStatus(it) },
                            )
                        }

                        if (seriesBooks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            BookSeriesCarousel(
                                seriesBooks = seriesBooks,
                                onBookClick = { item ->
                                    item.localBookId?.let(onOpenBookDetail)
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
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
