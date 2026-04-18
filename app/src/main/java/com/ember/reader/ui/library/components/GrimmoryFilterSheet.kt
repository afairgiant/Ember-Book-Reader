package com.ember.reader.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ember.reader.core.grimmory.GrimmoryAppFilterOptions
import com.ember.reader.core.grimmory.GrimmoryFilter
import com.ember.reader.core.grimmory.GrimmorySortKey
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.grimmory.SortDirection

/** Server-side sort/filter sheet for remote Grimmory library views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrimmoryFilterSheet(
    filter: GrimmoryFilter,
    filterOptions: GrimmoryAppFilterOptions?,
    onApply: (GrimmoryFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local draft so the user can tweak without every change triggering a network call.
    var draft by remember(filter) { mutableStateOf(filter) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sort & filter",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onReset()
                    onDismiss()
                }) { Text("Reset") }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text("Sort by", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            GrimmorySortKey.values().forEach { key ->
                val isSelected = draft.sort == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { draft = draft.copy(sort = key) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (key) {
                            GrimmorySortKey.ADDED -> "Recently added"
                            GrimmorySortKey.TITLE -> "Title"
                            GrimmorySortKey.SERIES -> "Series"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Direction", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                SortDirection.values().forEach { dir ->
                    val isSelected = draft.direction == dir
                    AssistChip(
                        onClick = { draft = draft.copy(direction = dir) },
                        label = {
                            Text(
                                when (dir) {
                                    SortDirection.ASC -> "Asc"
                                    SortDirection.DESC -> "Desc"
                                },
                            )
                        },
                        colors = if (isSelected) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Read status", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            val statusOptions: List<ReadStatus?> = listOf(
                null,
                ReadStatus.UNREAD,
                ReadStatus.READING,
                ReadStatus.RE_READING,
                ReadStatus.PARTIALLY_READ,
                ReadStatus.PAUSED,
                ReadStatus.READ,
                ReadStatus.ABANDONED,
                ReadStatus.WONT_READ,
                ReadStatus.UNSET,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(statusOptions) { status ->
                    val isSelected = draft.status == status
                    AssistChip(
                        onClick = { draft = draft.copy(status = status) },
                        label = {
                            Text(
                                when (status) {
                                    null -> "Any"
                                    ReadStatus.UNREAD -> "Unread"
                                    ReadStatus.READING -> "Reading"
                                    ReadStatus.RE_READING -> "Re-reading"
                                    ReadStatus.READ -> "Read"
                                    ReadStatus.PARTIALLY_READ -> "Partial"
                                    ReadStatus.PAUSED -> "Paused"
                                    ReadStatus.WONT_READ -> "Won't read"
                                    ReadStatus.ABANDONED -> "Abandoned"
                                    ReadStatus.UNSET -> "Unset"
                                },
                            )
                        },
                        colors = if (isSelected) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Personal rating (0–5)", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.minRating?.toString().orEmpty(),
                    onValueChange = { text ->
                        draft = draft.copy(
                            minRating = text.toIntOrNull()?.coerceIn(0, 5),
                        )
                    },
                    label = { Text("Min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.maxRating?.toString().orEmpty(),
                    onValueChange = { text ->
                        draft = draft.copy(
                            maxRating = text.toIntOrNull()?.coerceIn(0, 5),
                        )
                    },
                    label = { Text("Max") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            val authors = filterOptions?.authors.orEmpty()
            if (authors.isNotEmpty()) {
                var authorMenuOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft.authors ?: "Any",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Author") },
                        trailingIcon = {
                            Icon(
                                if (authorMenuOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { authorMenuOpen = true },
                    )
                    // Invisible click overlay — OutlinedTextField in readOnly still blocks clicks.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { authorMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = authorMenuOpen,
                        onDismissRequest = { authorMenuOpen = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any") },
                            onClick = {
                                draft = draft.copy(authors = null)
                                authorMenuOpen = false
                            },
                        )
                        authors.forEach { author ->
                            DropdownMenuItem(
                                text = { Text("${author.name} (${author.count})") },
                                onClick = {
                                    draft = draft.copy(authors = author.name)
                                    authorMenuOpen = false
                                },
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft.authors.orEmpty(),
                    onValueChange = { draft = draft.copy(authors = it.takeIf { s -> s.isNotBlank() }) },
                    label = { Text("Author (exact)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            val languages = filterOptions?.languages.orEmpty()
            if (languages.isNotEmpty()) {
                var langMenuOpen by remember { mutableStateOf(false) }
                val selectedLabel = when (val code = draft.language) {
                    null -> "Any"
                    else -> languages.firstOrNull { it.code == code }
                        ?.let { "${it.label ?: it.code}" }
                        ?: code
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = {
                            Icon(
                                if (langMenuOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { langMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = langMenuOpen,
                        onDismissRequest = { langMenuOpen = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any") },
                            onClick = {
                                draft = draft.copy(language = null)
                                langMenuOpen = false
                            },
                        )
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.label ?: lang.code} (${lang.count})") },
                                onClick = {
                                    draft = draft.copy(language = lang.code)
                                    langMenuOpen = false
                                },
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft.language.orEmpty(),
                    onValueChange = { draft = draft.copy(language = it.takeIf { s -> s.isNotBlank() }) },
                    label = { Text("Language code (e.g. en)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Apply") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
