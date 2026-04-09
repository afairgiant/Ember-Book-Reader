package com.ember.reader.ui.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.ember.reader.R
import com.ember.reader.core.repository.LibraryDensity
import com.ember.reader.core.repository.LibraryFormat
import com.ember.reader.core.repository.LibraryGroupBy
import com.ember.reader.core.repository.LibraryPrefs
import com.ember.reader.core.repository.LibrarySortKey
import com.ember.reader.core.repository.LibrarySource
import com.ember.reader.core.repository.LibraryStatus
import com.ember.reader.core.repository.LibraryViewMode
import com.ember.reader.ui.library.LibraryPreset

/** Active filter chip displayed in the toolbar row. */
data class ActiveFilterChip(val key: String, val label: String, val onClear: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolbar(
    resultCount: Int,
    activeChips: List<ActiveFilterChip>,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    onLayoutClick: () -> Unit,
    viewMode: LibraryViewMode,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = searchActive,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "toolbar-search-morph",
        ) { isSearching ->
            if (isSearching) {
                InlineSearchRow(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onSearchToggle,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.library_count, resultCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        activeChips.forEach { chip ->
                            AssistChip(
                                onClick = chip.onClear,
                                label = { Text(chip.label, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = onSearchToggle) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter))
                    }
                    IconButton(onClick = onSortClick) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                    }
                    IconButton(onClick = onLayoutClick) {
                        Icon(
                            when (viewMode) {
                                LibraryViewMode.GRID -> Icons.Default.GridView
                                LibraryViewMode.LIST -> Icons.Default.ViewList
                                LibraryViewMode.COMPACT_LIST -> Icons.Default.ViewAgenda
                            },
                            contentDescription = stringResource(R.string.layout),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_books)) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.close_search))
        }
    }
}

// ============================================================================
// Sort menu
// ============================================================================

@Composable
fun LibrarySortMenu(
    expanded: Boolean,
    prefs: LibraryPrefs,
    onDismiss: () -> Unit,
    onSortSelected: (LibrarySortKey) -> Unit,
    onGroupSelected: (LibraryGroupBy) -> Unit,
) {
    var groupSubExpanded by remember { mutableStateOf(false) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sort_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LibrarySortKey.values().forEach { key ->
            val selected = prefs.sortKey == key
            DropdownMenuItem(
                text = { Text(sortLabel(key)) },
                onClick = { onSortSelected(key) },
                trailingIcon = {
                    if (selected) {
                        Icon(
                            if (prefs.sortReversed) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.group_by) + ": " + groupLabel(prefs.groupBy)) },
            onClick = { groupSubExpanded = true },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(
            expanded = groupSubExpanded,
            onDismissRequest = { groupSubExpanded = false },
        ) {
            LibraryGroupBy.values().forEach { g ->
                DropdownMenuItem(
                    text = { Text(groupLabel(g)) },
                    onClick = {
                        onGroupSelected(g)
                        groupSubExpanded = false
                        onDismiss()
                    },
                    trailingIcon = {
                        if (prefs.groupBy == g) Icon(Icons.Default.Check, contentDescription = null)
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
}

// ============================================================================
// Layout popup (bottom sheet for breathing room)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryLayoutSheet(
    prefs: LibraryPrefs,
    onDismiss: () -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onDensityChange: (LibraryDensity) -> Unit,
    onShowContinueReadingChange: (Boolean) -> Unit,
    onCardShowProgressChange: (Boolean) -> Unit,
    onCardShowAuthorChange: (Boolean) -> Unit,
    onCardShowSourceBadgeChange: (Boolean) -> Unit,
    onCardShowFormatBadgeChange: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.layout), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // View mode
            Text(stringResource(R.string.view_mode), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedRow(
                options = LibraryViewMode.values().toList(),
                selected = prefs.viewMode,
                onSelect = onViewModeChange,
                labelOf = {
                    when (it) {
                        LibraryViewMode.GRID -> stringResource(R.string.view_grid)
                        LibraryViewMode.LIST -> stringResource(R.string.view_list)
                        LibraryViewMode.COMPACT_LIST -> stringResource(R.string.view_compact)
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Density
            Text(stringResource(R.string.density), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedRow(
                options = LibraryDensity.values().toList(),
                selected = prefs.density,
                onSelect = onDensityChange,
                labelOf = {
                    when (it) {
                        LibraryDensity.SMALL -> "S"
                        LibraryDensity.MEDIUM -> "M"
                        LibraryDensity.LARGE -> "L"
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            ToggleRow(
                label = stringResource(R.string.show_continue_reading),
                checked = prefs.showContinueReading,
                onCheckedChange = onShowContinueReadingChange,
            )
            Text(
                stringResource(R.string.card_info),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            ToggleRow(
                label = stringResource(R.string.card_progress),
                checked = prefs.cardShowProgress,
                onCheckedChange = onCardShowProgressChange,
            )
            ToggleRow(
                label = stringResource(R.string.card_author),
                checked = prefs.cardShowAuthor,
                onCheckedChange = onCardShowAuthorChange,
            )
            ToggleRow(
                label = stringResource(R.string.card_source_badge),
                checked = prefs.cardShowSourceBadge,
                onCheckedChange = onCardShowSourceBadgeChange,
            )
            ToggleRow(
                label = stringResource(R.string.card_format_badge),
                checked = prefs.cardShowFormatBadge,
                onCheckedChange = onCardShowFormatBadgeChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        options.forEach { opt ->
            val isSelected = opt == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(opt) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(opt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ============================================================================
// Filter sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    prefs: LibraryPrefs,
    formatCounts: Map<LibraryFormat, Int>,
    sourceCounts: Map<LibrarySource, Int>,
    statusCounts: Map<LibraryStatus, Int>,
    onDismiss: () -> Unit,
    onSourceChange: (LibrarySource) -> Unit,
    onFormatChange: (LibraryFormat) -> Unit,
    onStatusChange: (LibraryStatus) -> Unit,
    onApplyPreset: (LibraryPreset) -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onClearAll()
                    onDismiss()
                }) { Text(stringResource(R.string.clear_filters)) }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Quick presets
            Text(stringResource(R.string.quick_presets), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(LibraryPreset.values().toList()) { preset ->
                    AssistChip(
                        onClick = {
                            onApplyPreset(preset)
                            onDismiss()
                        },
                        label = { Text(presetLabel(preset)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Format
            FilterSection(
                title = stringResource(R.string.format),
                options = LibraryFormat.values().toList(),
                selected = prefs.formatFilter,
                onSelect = onFormatChange,
                labelOf = {
                    when (it) {
                        LibraryFormat.ALL -> stringResource(R.string.filter_all)
                        LibraryFormat.BOOKS -> stringResource(R.string.filter_books)
                        LibraryFormat.AUDIOBOOKS -> stringResource(R.string.filter_audiobooks)
                    }
                },
                countOf = { formatCounts[it] ?: 0 },
            )

            // Source
            FilterSection(
                title = stringResource(R.string.source),
                options = LibrarySource.values().toList(),
                selected = prefs.sourceFilter,
                onSelect = onSourceChange,
                labelOf = {
                    when (it) {
                        LibrarySource.ALL -> stringResource(R.string.filter_all)
                        LibrarySource.SERVER -> stringResource(R.string.filter_server)
                        LibrarySource.LOCAL -> stringResource(R.string.filter_local)
                    }
                },
                countOf = { sourceCounts[it] ?: 0 },
            )

            // Status
            FilterSection(
                title = stringResource(R.string.status),
                options = LibraryStatus.values().toList(),
                selected = prefs.statusFilter,
                onSelect = onStatusChange,
                labelOf = {
                    when (it) {
                        LibraryStatus.ALL -> stringResource(R.string.filter_all)
                        LibraryStatus.READING -> stringResource(R.string.status_reading)
                        LibraryStatus.UNREAD -> stringResource(R.string.status_unread)
                        LibraryStatus.FINISHED -> stringResource(R.string.status_finished)
                    }
                },
                countOf = { statusCounts[it] ?: 0 },
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun <T> FilterSection(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    countOf: (T) -> Int,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        options.forEach { opt ->
            val isSelected = opt == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(opt) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = labelOf(opt),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = countOf(opt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun presetLabel(preset: LibraryPreset): String = when (preset) {
    LibraryPreset.CURRENTLY_READING -> stringResource(R.string.preset_currently_reading)
    LibraryPreset.UNREAD -> stringResource(R.string.preset_unread)
    LibraryPreset.FINISHED -> stringResource(R.string.preset_finished)
    LibraryPreset.DOWNLOADED -> stringResource(R.string.preset_downloaded)
    LibraryPreset.AUDIOBOOKS -> stringResource(R.string.preset_audiobooks)
}

// ============================================================================
// Grimmory filter sheet — server-side sort/filter for remote library views.
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrimmoryFilterSheet(
    filter: com.ember.reader.ui.library.GrimmoryFilter,
    filterOptions: com.ember.reader.core.grimmory.GrimmoryAppFilterOptions?,
    onApply: (com.ember.reader.ui.library.GrimmoryFilter) -> Unit,
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

            // Sort key
            Text("Sort by", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            com.ember.reader.ui.library.GrimmorySortKey.values().forEach { key ->
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
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (key) {
                            com.ember.reader.ui.library.GrimmorySortKey.ADDED -> "Recently added"
                            com.ember.reader.ui.library.GrimmorySortKey.TITLE -> "Title"
                            com.ember.reader.ui.library.GrimmorySortKey.SERIES -> "Series"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Direction toggle
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Direction", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                com.ember.reader.ui.library.SortDirection.values().forEach { dir ->
                    val isSelected = draft.direction == dir
                    AssistChip(
                        onClick = { draft = draft.copy(direction = dir) },
                        label = {
                            Text(
                                when (dir) {
                                    com.ember.reader.ui.library.SortDirection.ASC -> "Asc"
                                    com.ember.reader.ui.library.SortDirection.DESC -> "Desc"
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

            // Read status
            Text("Read status", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            val statusOptions: List<com.ember.reader.core.grimmory.ReadStatus?> = listOf(
                null,
                com.ember.reader.core.grimmory.ReadStatus.UNREAD,
                com.ember.reader.core.grimmory.ReadStatus.READING,
                com.ember.reader.core.grimmory.ReadStatus.RE_READING,
                com.ember.reader.core.grimmory.ReadStatus.PARTIALLY_READ,
                com.ember.reader.core.grimmory.ReadStatus.PAUSED,
                com.ember.reader.core.grimmory.ReadStatus.READ,
                com.ember.reader.core.grimmory.ReadStatus.ABANDONED,
                com.ember.reader.core.grimmory.ReadStatus.WONT_READ,
                com.ember.reader.core.grimmory.ReadStatus.UNSET,
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
                                    com.ember.reader.core.grimmory.ReadStatus.UNREAD -> "Unread"
                                    com.ember.reader.core.grimmory.ReadStatus.READING -> "Reading"
                                    com.ember.reader.core.grimmory.ReadStatus.RE_READING -> "Re-reading"
                                    com.ember.reader.core.grimmory.ReadStatus.READ -> "Read"
                                    com.ember.reader.core.grimmory.ReadStatus.PARTIALLY_READ -> "Partial"
                                    com.ember.reader.core.grimmory.ReadStatus.PAUSED -> "Paused"
                                    com.ember.reader.core.grimmory.ReadStatus.WONT_READ -> "Won't read"
                                    com.ember.reader.core.grimmory.ReadStatus.ABANDONED -> "Abandoned"
                                    com.ember.reader.core.grimmory.ReadStatus.UNSET -> "Unset"
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

            // Rating range
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
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
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
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            // Authors — dropdown when filter options available, text field otherwise
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

            // Language — dropdown when filter options available
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
                androidx.compose.material3.Button(
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

