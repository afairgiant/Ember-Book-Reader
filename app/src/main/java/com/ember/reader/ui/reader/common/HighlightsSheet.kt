package com.ember.reader.ui.reader.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsSheet(
    highlights: List<Highlight>,
    onNavigate: (Highlight) -> Unit,
    onEdit: (Highlight) -> Unit = {},
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    repairState: HighlightRepairState = HighlightRepairState.Idle,
    /** Null when highlights in this book cannot be repaired (not a reflowable EPUB). */
    onRepair: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.highlights_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (onRepair != null) {
                HighlightRepairRow(
                    lackingCfi = highlights.count { it.cfi == null },
                    state = repairState,
                    onRepair = onRepair,
                )
            }
            if (highlights.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_highlights),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                )
            } else {
                LazyColumn {
                    items(highlights, key = { it.id }) { highlight ->
                        HighlightItem(
                            highlight = highlight,
                            onClick = { onNavigate(highlight) },
                            onEdit = { onEdit(highlight) },
                            onDelete = { onDelete(highlight.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Offers the repair when some highlights have no real CFI, and reports how it went.
 * Those highlights are the ones Grimmory's web reader cannot draw.
 */
@Composable
private fun HighlightRepairRow(lackingCfi: Int, state: HighlightRepairState, onRepair: () -> Unit) {
    val message = when (state) {
        HighlightRepairState.Running -> stringResource(R.string.highlight_repair_running)
        HighlightRepairState.Failed -> stringResource(R.string.highlight_repair_failed)
        is HighlightRepairState.Done ->
            if (state.unresolved == 0) {
                stringResource(R.string.highlight_repair_done, state.repaired)
            } else {
                stringResource(R.string.highlight_repair_done_partial, state.repaired, state.unresolved)
            }
        HighlightRepairState.Idle ->
            if (lackingCfi == 0) return else stringResource(R.string.highlight_repair_offer, lackingCfi)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state == HighlightRepairState.Idle || state == HighlightRepairState.Failed) {
            TextButton(onClick = onRepair) {
                Text(stringResource(R.string.highlight_repair_action))
            }
        }
    }
}

@Composable
private fun HighlightItem(
    highlight: Highlight,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(highlight.color.argb))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            // Show selected text in quotes
            val displayText = highlight.selectedText
            if (displayText != null) {
                Text(
                    text = "\u201C$displayText\u201D",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Show annotation below if present
            highlight.annotation?.let {
                if (displayText != null) Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Fallback if no text and no annotation
            if (displayText == null && highlight.annotation == null) {
                Text(
                    text = stringResource(R.string.highlight_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_highlight),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighlightColorPicker(selectedColor: HighlightColor, onColorSelected: (HighlightColor) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HighlightColor.entries.forEach { color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color.argb))
                    .then(
                        if (color == selectedColor) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}
