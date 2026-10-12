package com.ember.reader.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.reader.R
import com.ember.reader.core.repository.LibrarySourceFilter
import com.ember.reader.core.repository.LibraryStatus
import com.ember.reader.core.repository.LibraryViewMode
import com.ember.reader.core.repository.ServerAppearance
import com.ember.reader.ui.library.LibraryTab
import com.ember.reader.ui.theme.LocalAccentColor
import com.ember.reader.ui.theme.serverAccentColor

/** What the header shows and reports back; grouped so the screen passes one value down. */
data class LibraryHeaderState(
    val libraryCount: Int,
    val resultCount: Int,
    val viewMode: LibraryViewMode,
    val importing: Boolean,
    val sourceFilter: LibrarySourceFilter,
    val sourceOptions: List<LibrarySourceFilter>,
    val sourceCounts: Map<LibrarySourceFilter, Int>,
    val appearances: Map<Long, ServerAppearance>,
    val statusFilter: LibraryStatus,
    val shelfCounts: Map<LibraryStatus, Int>,
    val tab: LibraryTab,
    val openGroup: String?,
    /** A filter or grouping set in the sheet is in effect, which the header itself does not show. */
    val sheetFiltersActive: Boolean,
)

class LibraryHeaderActions(
    val onLayoutClick: () -> Unit,
    val onImportClick: () -> Unit,
    val onSourceChange: (LibrarySourceFilter) -> Unit,
    val onStatusChange: (LibraryStatus) -> Unit,
    val onTabChange: (LibraryTab) -> Unit,
    val onCloseGroup: () -> Unit,
    val onFilterClick: () -> Unit,
)

/**
 * Everything above the books: the title, the source and status the library is narrowed to, the
 * Books / Series / Authors tabs, and the filter button. It scrolls away with the list.
 */
@Composable
fun LibraryHeader(state: LibraryHeaderState, actions: LibraryHeaderActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        TitleRow(state, actions)

        // "All" and "Local" are always there; the pill only earns its place with a server too.
        if (state.sourceOptions.size > 2) {
            Spacer(modifier = Modifier.height(12.dp))
            SourcePill(state, actions.onSourceChange)
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.library_shelves))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryStatus.values().forEach { status ->
                ShelfChip(
                    title = shelfLabel(status),
                    count = state.shelfCounts[status] ?: 0,
                    selected = status == state.statusFilter,
                    onClick = { actions.onStatusChange(status) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LibraryTab.values().forEach { tab ->
                val selected = tab == state.tab
                Text(
                    text = tabLabel(tab),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { actions.onTabChange(tab) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        state.openGroup?.let { group ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = actions.onCloseGroup)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = group,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterPill(active = state.sheetFiltersActive, onClick = actions.onFilterClick)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = resultCountLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun TitleRow(state: LibraryHeaderState, actions: LibraryHeaderActions) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(
                pluralStringResource(R.plurals.library_in_your_library, state.libraryCount, state.libraryCount)
            )
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = actions.onLayoutClick) {
            Icon(
                when (state.viewMode) {
                    LibraryViewMode.GRID -> Icons.Default.GridView
                    LibraryViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                    LibraryViewMode.COMPACT_LIST -> Icons.Default.ViewAgenda
                },
                contentDescription = stringResource(R.string.layout),
            )
        }
        IconButton(onClick = actions.onImportClick, enabled = !state.importing) {
            if (state.importing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_book))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SourcePill(state: LibraryHeaderState, onSourceChange: (LibrarySourceFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(min = 180.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceDot(state.sourceFilter, state.appearances)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.source),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sourceLabel(state.sourceFilter, state.appearances),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.sourceOptions.forEach { option ->
                DropdownMenuItem(
                    leadingIcon = { SourceDot(option, state.appearances) },
                    text = { Text(sourceLabel(option, state.appearances)) },
                    trailingIcon = {
                        Text(
                            text = (state.sourceCounts[option] ?: 0).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSourceChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceDot(source: LibrarySourceFilter, appearances: Map<Long, ServerAppearance>) {
    val color: Color = when (source) {
        LibrarySourceFilter.All -> MaterialTheme.colorScheme.outline
        LibrarySourceFilter.Local -> LocalAccentColor
        is LibrarySourceFilter.Server ->
            appearances[source.serverId]?.colorSlot?.let(::serverAccentColor)
                ?: MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun sourceLabel(source: LibrarySourceFilter, appearances: Map<Long, ServerAppearance>): String =
    when (source) {
        LibrarySourceFilter.All -> stringResource(R.string.library_all_sources)
        LibrarySourceFilter.Local -> stringResource(R.string.filter_local)
        is LibrarySourceFilter.Server -> appearances[source.serverId]?.name ?: ""
    }

@Composable
private fun ShelfChip(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            accent.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.library_books, count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterPill(active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sort_and_filter), style = MaterialTheme.typography.labelLarge)
            if (active) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun shelfLabel(status: LibraryStatus): String = when (status) {
    LibraryStatus.ALL -> stringResource(R.string.library_everything)
    LibraryStatus.READING -> stringResource(R.string.preset_currently_reading)
    LibraryStatus.UNREAD -> stringResource(R.string.status_unread)
    LibraryStatus.FINISHED -> stringResource(R.string.status_finished)
}

@Composable
private fun tabLabel(tab: LibraryTab): String = when (tab) {
    LibraryTab.BOOKS -> stringResource(R.string.library_tab_books)
    LibraryTab.SERIES -> stringResource(R.string.group_series)
    LibraryTab.AUTHORS -> stringResource(R.string.library_tab_authors)
}

@Composable
private fun resultCountLabel(state: LibraryHeaderState): String {
    val count = state.resultCount
    return when {
        state.tab == LibraryTab.BOOKS || state.openGroup != null ->
            pluralStringResource(R.plurals.library_books, count, count)
        state.tab == LibraryTab.SERIES -> pluralStringResource(R.plurals.library_series, count, count)
        else -> pluralStringResource(R.plurals.library_authors, count, count)
    }
}

/** The search field that takes the header's place at the top of the screen while searching. */
@Composable
fun LibrarySearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = modifier
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

@Preview(showBackground = true)
@Composable
private fun LibraryHeaderPreview() {
    val server = LibrarySourceFilter.Server(1L)
    LibraryHeader(
        state = LibraryHeaderState(
            libraryCount = 48,
            resultCount = 12,
            viewMode = LibraryViewMode.GRID,
            importing = false,
            sourceFilter = LibrarySourceFilter.All,
            sourceOptions = listOf(LibrarySourceFilter.All, LibrarySourceFilter.Local, server),
            sourceCounts = mapOf(LibrarySourceFilter.All to 48, LibrarySourceFilter.Local to 3, server to 45),
            appearances = emptyMap(),
            statusFilter = LibraryStatus.READING,
            shelfCounts = mapOf(
                LibraryStatus.ALL to 48,
                LibraryStatus.READING to 12,
                LibraryStatus.UNREAD to 30,
                LibraryStatus.FINISHED to 6,
            ),
            tab = LibraryTab.BOOKS,
            openGroup = null,
            sheetFiltersActive = true,
        ),
        actions = LibraryHeaderActions({}, {}, {}, {}, {}, {}, {}),
        modifier = Modifier.padding(16.dp),
    )
}
