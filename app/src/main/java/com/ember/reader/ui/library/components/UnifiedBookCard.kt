package com.ember.reader.ui.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.ServerAppearance
import com.ember.reader.ui.common.BookCoverImage
import com.ember.reader.ui.theme.LocalAccentColor
import com.ember.reader.ui.theme.serverAccentColor

data class CardInfoToggles(
    val showProgress: Boolean = true,
    val showAuthor: Boolean = true,
    val showFormatBadge: Boolean = false,
    /**
     * When true, renders a dim subtitle line with whichever of page count, file
     * size, and published year the server populated (`AppBookSummary` exposes
     * these on Grimmory v3.0.0+). Off by default so existing views don't
     * change shape.
     */
    val showMetadata: Boolean = false
)

/**
 * Builds the "350 pp · 2.5 MB · 2025" line rendered under the author when
 * [CardInfoToggles.showMetadata] is on. Returns null if no fields are
 * populated so the caller can skip the row entirely.
 */
private fun metadataLine(book: Book): String? {
    val parts = buildList {
        book.pageCount?.takeIf { it > 0 }?.let { add("$it pp") }
        book.fileSizeKb?.takeIf { it > 0 }?.let { add(formatFileSize(it)) }
        // `publishedDate` is an ISO date string; the year alone is the
        // useful part for a library subtitle.
        book.publishedDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }?.let(::add)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun formatFileSize(kb: Long): String = when {
    kb >= 1024 * 1024 -> "%.1f GB".format(kb / 1024.0 / 1024.0)
    kb >= 1024 -> "%.1f MB".format(kb / 1024.0)
    else -> "$kb KB"
}

enum class SourceBadgeStyle { GRID, LIST_PILL, COMPACT_DOT }

private const val SOURCE_BADGE_MAX_CHARS = 12

/**
 * Per-server identifier rendered on a book surface. [appearance] == null renders
 * as "Local" with [LocalAccentColor].
 */
@Composable
fun SourceBadge(
    appearance: ServerAppearance?,
    style: SourceBadgeStyle,
    modifier: Modifier = Modifier,
    localLabel: String = stringResource(R.string.filter_local)
) {
    val rawLabel = appearance?.name ?: localLabel
    val label = if (rawLabel.length > SOURCE_BADGE_MAX_CHARS) {
        rawLabel.take(SOURCE_BADGE_MAX_CHARS).trimEnd() + "…"
    } else {
        rawLabel
    }
    val color = appearance?.let { serverAccentColor(it.colorSlot) } ?: LocalAccentColor
    val description = stringResource(R.string.chip_source, rawLabel)

    when (style) {
        SourceBadgeStyle.GRID -> LabelledBadge(
            label = label,
            fill = color,
            description = description,
            cornerRadius = 6.dp,
            maxWidth = 140.dp,
            horizontalPadding = 6.dp,
            verticalPadding = 2.dp,
            dotSpacing = 5.dp,
            modifier = modifier
        )
        SourceBadgeStyle.LIST_PILL -> LabelledBadge(
            label = label,
            fill = color,
            description = description,
            cornerRadius = 12.dp,
            maxWidth = 110.dp,
            horizontalPadding = 8.dp,
            verticalPadding = 3.dp,
            dotSpacing = 4.dp,
            modifier = modifier
        )
        SourceBadgeStyle.COMPACT_DOT -> Box(
            modifier = modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
                .semantics { contentDescription = description }
        )
    }
}

@Composable
private fun LabelledBadge(
    label: String,
    fill: Color,
    description: String,
    cornerRadius: androidx.compose.ui.unit.Dp,
    maxWidth: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    dotSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(fill.copy(alpha = 0.9f))
            .widthIn(max = maxWidth)
            .semantics { contentDescription = description }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.9f))
            )
            Spacer(modifier = Modifier.width(dotSpacing))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Grid cell: the cover with its progress along the bottom edge, the title and author beneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedBookCard(
    book: Book,
    progress: Float? = null,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    info: CardInfoToggles = CardInfoToggles(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRelink: (() -> Unit)? = null,
    onPullProgress: (() -> Unit)? = null,
    onPushProgress: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val coverShape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(coverShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(coverShape)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, coverShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            BookCoverImage(book = book, modifier = Modifier.fillMaxSize())

            if (isSelecting) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onRelink != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.relink_to_server)) },
                                onClick = {
                                    showMenu = false
                                    onRelink()
                                }
                            )
                        }
                        if (onPullProgress != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pull_progress)) },
                                onClick = {
                                    showMenu = false
                                    onPullProgress()
                                }
                            )
                        }
                        if (onPushProgress != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.push_progress)) },
                                onClick = {
                                    showMenu = false
                                    onPushProgress()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (info.showFormatBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = book.format.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            if (info.showProgress && progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    trackColor = Color.Black.copy(alpha = 0.45f)
                )
            }
        }

        Column(modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (info.showAuthor) {
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (info.showMetadata) {
                metadataLine(book)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Compact list row used by LIST and COMPACT_LIST view modes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedBookListRow(
    book: Book,
    progress: Float? = null,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    compact: Boolean = false,
    info: CardInfoToggles = CardInfoToggles(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRelink: (() -> Unit)? = null,
    onPullProgress: (() -> Unit)? = null,
    onPushProgress: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val coverWidth = if (compact) 0.dp else 44.dp
    val coverHeight = if (compact) 0.dp else 64.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!compact) {
                BookCoverImage(
                    book = book,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.showAuthor) {
                    book.author?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (info.showMetadata) {
                    metadataLine(book)?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (info.showProgress && progress != null && progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }

            if (isSelecting) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (onRelink != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.relink_to_server)) },
                            onClick = {
                                showMenu = false
                                onRelink()
                            }
                        )
                    }
                    if (onPullProgress != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pull_progress)) },
                            onClick = {
                                showMenu = false
                                onPullProgress()
                            }
                        )
                    }
                    if (onPushProgress != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.push_progress)) },
                            onClick = {
                                showMenu = false
                                onPushProgress()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
