package com.ember.reader.ui.reader.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ember.reader.core.grimmory.AudiobookChapter
import com.ember.reader.core.grimmory.AudiobookTrack
import com.ember.reader.ui.common.BookCoverPlaceholderColors
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen
import com.ember.reader.ui.common.bookCoverColorIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrackIndex by viewModel.currentTrackIndex.collectAsStateWithLifecycle()

    var showChapters by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    when (val state = uiState) {
        AudiobookUiState.Loading -> LoadingScreen()
        is AudiobookUiState.Error -> ErrorScreen(state.message)
        is AudiobookUiState.Ready -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Blurred cover backdrop (API 31+ only — hardware-accelerated blur)
                val coverUrl = state.book.coverUrl
                if (coverUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(30.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Cover art
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUrl != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = state.book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        val colorIndex = bookCoverColorIndex(state.book.title)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(BookCoverPlaceholderColors[colorIndex]),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.book.title.take(2).uppercase(),
                                style = MaterialTheme.typography.displayLarge,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title + Author + Narrator
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.book.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    state.book.author?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    state.info?.narrator?.let {
                        Text(
                            text = "Narrated by $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Current chapter
                    val currentChapter = chapters.lastOrNull { it.startTimeMs <= currentPositionMs }
                    currentChapter?.title?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Progress slider
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Slider(
                        value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                        onValueChange = { fraction ->
                            viewModel.seekTo((fraction * durationMs).toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        viewModel.setSpeed(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Skip back 30s
                    IconButton(onClick = viewModel::skipBackward) {
                        Icon(
                            Icons.Default.Replay30,
                            contentDescription = "Rewind 30s",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Previous chapter
                    IconButton(onClick = viewModel::previousChapter) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous chapter",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause (large)
                    IconButton(
                        onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next chapter
                    IconButton(onClick = viewModel::nextChapter) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next chapter",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Skip forward 30s
                    IconButton(onClick = viewModel::skipForward) {
                        Icon(
                            Icons.Default.Forward30,
                            contentDescription = "Forward 30s",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Chapters / track list button
                    if (chapters.isNotEmpty() || tracks.isNotEmpty()) {
                        IconButton(onClick = { showChapters = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = if (tracks.isNotEmpty()) "Tracks" else "Chapters",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Track / chapter list bottom sheet
            if (showChapters) {
                ModalBottomSheet(
                    onDismissRequest = { showChapters = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    if (tracks.isNotEmpty()) {
                        TrackListSheet(
                            tracks = tracks,
                            currentTrackIndex = currentTrackIndex,
                            onTrackSelected = { index ->
                                viewModel.seekToTrack(index)
                                showChapters = false
                            },
                        )
                    } else {
                        ChapterListSheet(
                            chapters = chapters,
                            currentPositionMs = currentPositionMs,
                            onChapterSelected = { chapter ->
                                viewModel.seekToChapter(chapter)
                                showChapters = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListSheet(
    tracks: List<AudiobookTrack>,
    currentTrackIndex: Int,
    onTrackSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = "Tracks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (currentTrackIndex - 2).coerceAtLeast(0),
        )
        LazyColumn(state = listState) {
            itemsIndexed(tracks) { index, track ->
                val isCurrent = index == currentTrackIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackSelected(index) }
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = track.title ?: track.fileName ?: "Track ${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formatTime(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterListSheet(
    chapters: List<AudiobookChapter>,
    currentPositionMs: Long,
    onChapterSelected: (AudiobookChapter) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val currentIndex = chapters.indexOfLast { it.startTimeMs <= currentPositionMs }
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0),
        )
        LazyColumn(state = listState) {
            items(chapters) { chapter ->
                val isCurrent = chapter.startTimeMs <= currentPositionMs &&
                    (chapter.endTimeMs > currentPositionMs || chapter == chapters.last())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterSelected(chapter) }
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chapter.title ?: "Chapter ${chapter.index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formatTime(chapter.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
