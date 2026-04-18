package com.ember.reader.ui.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.ServerAppearance
import com.ember.reader.ui.common.BookCoverImage
import com.ember.reader.ui.theme.LocalAccentColor
import com.ember.reader.ui.theme.serverAccentColor
import kotlin.math.roundToInt

data class CardInfoToggles(
    val showProgress: Boolean = true,
    val showAuthor: Boolean = true,
    val showSourceBadge: Boolean = true,
    val showFormatBadge: Boolean = false
)

enum class SourceBadgeStyle { GRID, LIST_PILL, COMPACT_DOT }

/**
 * Per-server identifier rendered on a book surface.
 *
 * [appearance] == null represents a local (non-server) book and always renders with
 * [LocalAccentColor] and the label "Local". Caller is responsible for wrapping in the
 * `if (info.showSourceBadge)` gate.
 */
@Composable
fun SourceBadge(
    appearance: ServerAppearance?,
    style: SourceBadgeStyle,
    modifier: Modifier = Modifier,
    localLabel: String = stringResource(R.string.filter_local)
) {
    val label = appearance?.name ?: localLabel
    val color = appearance?.let { serverAccentColor(it.colorSlot) } ?: LocalAccentColor
    val description = stringResource(R.string.chip_source, label)

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedBookCard(
    book: Book,
    progress: Float? = null,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    info: CardInfoToggles = CardInfoToggles(),
    sourceAppearance: ServerAppearance? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRelink: (() -> Unit)? = null,
    onPullProgress: (() -> Unit)? = null,
    onPushProgress: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

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
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                BookCoverImage(
                    book = book,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )

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

                if (info.showSourceBadge) {
                    SourceBadge(
                        appearance = sourceAppearance,
                        style = SourceBadgeStyle.GRID,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )
                }

                if (info.showFormatBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
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
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.showAuthor) {
                    book.author?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
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
    sourceAppearance: ServerAppearance? = null,
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

            if (compact && info.showSourceBadge) {
                SourceBadge(
                    appearance = sourceAppearance,
                    style = SourceBadgeStyle.COMPACT_DOT
                )
                Spacer(modifier = Modifier.width(8.dp))
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

            if (!compact && info.showSourceBadge) {
                SourceBadge(
                    appearance = sourceAppearance,
                    style = SourceBadgeStyle.LIST_PILL,
                    modifier = Modifier.padding(start = 8.dp)
                )
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
