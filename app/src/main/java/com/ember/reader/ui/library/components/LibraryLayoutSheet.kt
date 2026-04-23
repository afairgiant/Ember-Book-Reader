package com.ember.reader.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.repository.LibraryDensity
import com.ember.reader.core.repository.LibraryPrefs
import com.ember.reader.core.repository.LibraryViewMode

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
    onCardShowFormatBadgeChange: (Boolean) -> Unit,
    onCardShowMetadataChange: (Boolean) -> Unit,
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
                label = stringResource(R.string.card_format_badge),
                checked = prefs.cardShowFormatBadge,
                onCheckedChange = onCardShowFormatBadgeChange,
            )
            ToggleRow(
                label = stringResource(R.string.card_metadata),
                checked = prefs.cardShowMetadata,
                onCheckedChange = onCardShowMetadataChange,
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
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(opt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
