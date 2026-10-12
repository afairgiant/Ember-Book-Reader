package com.ember.reader.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.repository.LibraryFormat
import com.ember.reader.core.repository.LibraryGroupBy
import com.ember.reader.core.repository.LibraryPrefs
import com.ember.reader.core.repository.LibrarySortKey

/**
 * Sorting, grouping and the format filter. Source and reading status are chosen in the
 * library header, not here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    prefs: LibraryPrefs,
    onDismiss: () -> Unit,
    onSortSelected: (LibrarySortKey) -> Unit,
    onGroupSelected: (LibraryGroupBy) -> Unit,
    onFormatChange: (LibraryFormat) -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sort_and_filter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onClearAll()
                    onDismiss()
                }) { Text(stringResource(R.string.sheet_reset)) }
            }
            Spacer(modifier = Modifier.height(4.dp))

            FilterChipRow(
                title = stringResource(R.string.sort_by),
                options = LibrarySortKey.values().toList(),
                isSelected = { it == prefs.sortKey },
                onSelect = onSortSelected,
                labelOf = { sortLabel(it) },
                trailing = { opt ->
                    if (opt == prefs.sortKey) {
                        Icon(
                            imageVector = if (prefs.sortReversed) {
                                Icons.Default.ArrowDropUp
                            } else {
                                Icons.Default.ArrowDropDown
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )

            FilterChipRow(
                title = stringResource(R.string.group_by),
                options = LibraryGroupBy.values().toList(),
                isSelected = { it == prefs.groupBy },
                onSelect = onGroupSelected,
                labelOf = { groupLabel(it) },
            )

            FilterChipRow(
                title = stringResource(R.string.format),
                options = LibraryFormat.values().toList(),
                isSelected = { it == prefs.formatFilter },
                onSelect = onFormatChange,
                labelOf = {
                    when (it) {
                        LibraryFormat.ALL -> stringResource(R.string.filter_all)
                        LibraryFormat.BOOKS -> stringResource(R.string.filter_books)
                        LibraryFormat.AUDIOBOOKS -> stringResource(R.string.filter_audiobooks)
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterChipRow(
    title: String,
    options: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    trailing: @Composable (T) -> Unit = {},
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { opt ->
                val selected = isSelected(opt)
                AssistChip(
                    onClick = { onSelect(opt) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(text = labelOf(opt))
                            trailing(opt)
                        }
                    },
                    colors = if (selected) {
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
    }
}

@Composable
private fun sortLabel(key: LibrarySortKey): String = when (key) {
    LibrarySortKey.RECENT -> stringResource(R.string.sort_recent)
    LibrarySortKey.TITLE -> stringResource(R.string.sort_title)
    LibrarySortKey.AUTHOR -> stringResource(R.string.sort_author)
    LibrarySortKey.PROGRESS -> stringResource(R.string.sort_progress)
    LibrarySortKey.DATE_ADDED -> stringResource(R.string.sort_date_added)
    LibrarySortKey.FILE_SIZE -> stringResource(R.string.sort_file_size)
}

@Composable
private fun groupLabel(g: LibraryGroupBy): String = when (g) {
    LibraryGroupBy.NONE -> stringResource(R.string.group_none)
    LibraryGroupBy.AUTHOR -> stringResource(R.string.sort_author)
    LibraryGroupBy.SERIES -> stringResource(R.string.group_series)
    LibraryGroupBy.FORMAT -> stringResource(R.string.group_format)
    LibraryGroupBy.STATUS -> stringResource(R.string.group_status)
    LibraryGroupBy.DATE_ADDED -> stringResource(R.string.sort_date_added)
    LibraryGroupBy.SOURCE -> stringResource(R.string.group_source)
}
