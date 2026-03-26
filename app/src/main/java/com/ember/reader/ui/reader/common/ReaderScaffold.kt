package com.ember.reader.ui.reader.common
import kotlin.math.roundToInt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatListBulleted
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onSeekToProgression: (Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            ReaderTopBar(
                title = title,
                hasBookmark = hasBookmarkAtCurrentPosition,
                onNavigateBack = onNavigateBack,
                onToggleBookmark = onToggleBookmark,
                onOpenTableOfContents = onOpenTableOfContents,
                onOpenPreferences = onOpenPreferences,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            content()
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ReaderBottomBar(
                currentLocator = currentLocator,
                onSeekToProgression = onSeekToProgression,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    hasBookmark: Boolean,
    onNavigateBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTableOfContents: () -> Unit,
    onOpenPreferences: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenTableOfContents) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = "Table of Contents")
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                if (hasBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "Bookmark",
            )
        }
        IconButton(onClick = onOpenPreferences) {
            Icon(Icons.Default.Settings, contentDescription = "Reader Settings")
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentLocator: Locator?,
    onSeekToProgression: (Float) -> Unit = {},
) {
    val progression = currentLocator?.locations?.totalProgression?.toFloat() ?: 0f
    val chapterTitle = currentLocator?.title
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (chapterTitle != null) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${((if (dragging) dragValue else progression) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
