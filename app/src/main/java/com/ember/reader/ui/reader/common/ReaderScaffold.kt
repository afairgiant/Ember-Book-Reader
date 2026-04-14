package com.ember.reader.ui.reader.common
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import kotlin.math.abs
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Locator

@Composable
fun ReaderScaffold(
    title: String,
    chromeVisible: Boolean,
    currentLocator: Locator?,
    hasBookmarkAtCurrentPosition: Boolean,
    onNavigateBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTableOfContents: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenHighlights: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onSeekToProgression: (Float) -> Unit = {},
    brightness: Float = -1f,
    onBrightnessChange: (Float) -> Unit = {},
    streaming: Boolean = false,
    content: @Composable () -> Unit
) {
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var displayBrightness by remember { mutableFloatStateOf(if (brightness >= 0) brightness else 0.5f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content fills the entire screen — bars overlay on top
        content()

        // Left-edge brightness gesture strip
        if (!chromeVisible) {
            val view = LocalView.current
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(24.dp)
                    .fillMaxHeight()
                    .onGloballyPositioned { coordinates ->
                        // Exclude from system back gesture so touches reach the app
                        val rect = android.graphics.Rect(
                            0, 0,
                            coordinates.size.width,
                            coordinates.size.height
                        )
                        view.systemGestureExclusionRects = listOf(rect)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            displayBrightness = if (brightness >= 0) brightness else 0.5f
                            var dragging = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    if (dragging) showBrightnessIndicator = false
                                    break
                                }
                                val delta = change.positionChange()
                                if (!dragging && abs(delta.y) > 4f) {
                                    dragging = true
                                    showBrightnessIndicator = true
                                }
                                if (dragging) {
                                    change.consume()
                                    val heightPx = size.height.toFloat()
                                    val brightnessChange = -delta.y / heightPx
                                    val newBrightness = (displayBrightness + brightnessChange).coerceIn(0.01f, 1.0f)
                                    displayBrightness = newBrightness
                                    onBrightnessChange(newBrightness)
                                }
                            }
                        }
                    }
            )
        }

        // Brightness indicator
        if (showBrightnessIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 48.dp)
                    .width(36.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Background track
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
                // Fill level
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(displayBrightness)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                // Percentage label
                Text(
                    text = "${(displayBrightness * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-4).dp)
                )
            }
        }

        // Bookmark ribbon indicator hanging from top-right
        if (hasBookmarkAtCurrentPosition) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .width(24.dp)
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            bottomStart = 4.dp,
                            bottomEnd = 4.dp
                        )
                    )
            )
        }

        // Top bar overlaid at the top
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = title,
                hasBookmark = hasBookmarkAtCurrentPosition,
                onNavigateBack = onNavigateBack,
                onToggleBookmark = onToggleBookmark,
                onOpenTableOfContents = onOpenTableOfContents,
                onOpenHighlights = onOpenHighlights,
                onOpenBookmarks = onOpenBookmarks,
                onOpenPreferences = onOpenPreferences,
                onOpenSearch = onOpenSearch,
                streaming = streaming
            )
        }

        // Bottom bar overlaid at the bottom
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                currentLocator = currentLocator,
                onSeekToProgression = onSeekToProgression
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderTopBar(
    title: String,
    hasBookmark: Boolean,
    onNavigateBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTableOfContents: () -> Unit,
    onOpenHighlights: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenSearch: () -> Unit,
    streaming: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (streaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Streaming \u2014 not saved to device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_cd))
        }
        IconButton(onClick = onOpenTableOfContents) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = stringResource(R.string.table_of_contents_cd))
        }
        Icon(
            if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = stringResource(R.string.bookmark_cd),
            modifier = Modifier
                .combinedClickable(
                    onClick = onToggleBookmark,
                    onLongClick = onOpenBookmarks
                )
                .padding(12.dp)
                .size(24.dp)
        )
        IconButton(onClick = onOpenHighlights) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.highlights_cd))
        }
        IconButton(onClick = onOpenPreferences) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_settings_cd))
        }
    }
}

@Composable
private fun ReaderBottomBar(currentLocator: Locator?, onSeekToProgression: (Float) -> Unit = {}) {
    val progression = currentLocator?.locations?.totalProgression?.toFloat() ?: 0f
    val chapterTitle = currentLocator?.title
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (chapterTitle != null) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = if (dragging) dragValue else progression,
                onValueChange = { value ->
                    dragging = true
                    dragValue = value
                },
                onValueChangeFinished = {
                    onSeekToProgression(dragValue)
                    dragging = false
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${((if (dragging) dragValue else progression) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
