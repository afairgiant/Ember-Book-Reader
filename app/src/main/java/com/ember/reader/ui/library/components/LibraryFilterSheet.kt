package com.ember.reader.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.repository.LibraryFormat
import com.ember.reader.core.repository.LibraryGroupBy
import com.ember.reader.core.repository.LibraryPrefs
import com.ember.reader.core.repository.LibrarySortKey
import com.ember.reader.core.repository.LibrarySourceFilter
import com.ember.reader.core.repository.LibraryStatus
import com.ember.reader.core.repository.ServerAppearance
import com.ember.reader.ui.library.LibraryPreset
import com.ember.reader.ui.theme.LocalAccentColor
import com.ember.reader.ui.theme.serverAccentColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    prefs: LibraryPrefs,
    sourceOptions: List<LibrarySourceFilter>,
    appearances: Map<Long, ServerAppearance>,
    onDismiss: () -> Unit,
    onSortSelected: (LibrarySortKey) -> Unit,
    onGroupSelected: (LibraryGroupBy) -> Unit,
    onSourceChange: (LibrarySourceFilter) -> Unit,
    onFormatChange: (LibraryFormat) -> Unit,
    onStatusChange: (LibraryStatus) -> Unit,
    onApplyPreset: (LibraryPreset) -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activePreset = prefs.matchingPreset()
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
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(R.string.quick_presets), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(LibraryPreset.values().toList()) { preset ->
                    val selected = preset == activePreset
                    AssistChip(
                        onClick = {
                            onApplyPreset(preset)
                            onDismiss()
                        },
                        label = { Text(presetLabel(preset)) },
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

            FilterChipRow(
                title = stringResource(R.string.source),
                options = sourceOptions,
                isSelected = { it == prefs.sourceFilter },
                onSelect = onSourceChange,
                labelOf = {
                    when (it) {
                        LibrarySourceFilter.All -> stringResource(R.string.filter_all)
                        LibrarySourceFilter.Local -> stringResource(R.string.filter_local)
                        is LibrarySourceFilter.Server -> appearances[it.serverId]?.name ?: ""
                    }
                },
                leading = { opt ->
                    val dotColor: Color? = when (opt) {
                        LibrarySourceFilter.Local -> LocalAccentColor
                        is LibrarySourceFilter.Server ->
                            appearances[opt.serverId]?.colorSlot?.let(::serverAccentColor)
                        else -> null
                    }
                    if (dotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(dotColor),
                        )
                    }
                },
            )

            FilterChipRow(
                title = stringResource(R.string.status),
                options = LibraryStatus.values().toList(),
                isSelected = { it == prefs.statusFilter },
                onSelect = onStatusChange,
                labelOf = {
                    when (it) {
                        LibraryStatus.ALL -> stringResource(R.string.filter_all)
                        LibraryStatus.READING -> stringResource(R.string.status_reading)
                        LibraryStatus.UNREAD -> stringResource(R.string.status_unread)
                        LibraryStatus.FINISHED -> stringResource(R.string.status_finished)
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
    leading: @Composable (T) -> Unit = {},
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
                            leading(opt)
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

@Composable
private fun presetLabel(preset: LibraryPreset): String = when (preset) {
    LibraryPreset.CURRENTLY_READING -> stringResource(R.string.preset_currently_reading)
    LibraryPreset.UNREAD -> stringResource(R.string.preset_unread)
    LibraryPreset.FINISHED -> stringResource(R.string.preset_finished)
    LibraryPreset.DOWNLOADED -> stringResource(R.string.preset_downloaded)
    LibraryPreset.AUDIOBOOKS -> stringResource(R.string.preset_audiobooks)
}

/**
 * Returns the [LibraryPreset] whose field combination exactly matches the current prefs,
 * so the sheet can highlight the active preset chip. Must stay in sync with the
 * corresponding branches of `LocalLibraryViewModel.applyPreset`.
 */
private fun LibraryPrefs.matchingPreset(): LibraryPreset? = when {
    sourceFilter == LibrarySourceFilter.All &&
        formatFilter == LibraryFormat.ALL &&
        statusFilter == LibraryStatus.READING -> LibraryPreset.CURRENTLY_READING
    sourceFilter == LibrarySourceFilter.All &&
        formatFilter == LibraryFormat.ALL &&
        statusFilter == LibraryStatus.UNREAD -> LibraryPreset.UNREAD
    sourceFilter == LibrarySourceFilter.All &&
        formatFilter == LibraryFormat.ALL &&
        statusFilter == LibraryStatus.FINISHED -> LibraryPreset.FINISHED
    sourceFilter == LibrarySourceFilter.Local &&
        formatFilter == LibraryFormat.ALL &&
        statusFilter == LibraryStatus.ALL -> LibraryPreset.DOWNLOADED
    sourceFilter == LibrarySourceFilter.All &&
        formatFilter == LibraryFormat.AUDIOBOOKS &&
        statusFilter == LibraryStatus.ALL -> LibraryPreset.AUDIOBOOKS
    else -> null
}
