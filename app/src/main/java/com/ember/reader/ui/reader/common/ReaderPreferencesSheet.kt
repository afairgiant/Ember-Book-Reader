package com.ember.reader.ui.reader.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderPreferencesSheet(
    preferences: ReaderPreferences,
    onPreferencesChanged: (ReaderPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Reader Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Font Family
            SectionLabel("Font")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FontFamily.entries.forEach { font ->
                    FilterChip(
                        selected = preferences.fontFamily == font,
                        onClick = { onPreferencesChanged(preferences.copy(fontFamily = font)) },
                        label = { Text(font.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Size
            SectionLabel("Font Size")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = {
                        val newSize = (preferences.fontSize - 1f).coerceAtLeast(12f)
                        onPreferencesChanged(preferences.copy(fontSize = newSize))
                    },
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text(
                    text = "${preferences.fontSize.toInt()}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = {
                        val newSize = (preferences.fontSize + 1f).coerceAtMost(32f)
                        onPreferencesChanged(preferences.copy(fontSize = newSize))
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Line Height
            SectionLabel("Line Height")
            Slider(
                value = preferences.lineHeight,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(lineHeight = it))
                },
                valueRange = 1.0f..2.5f,
                steps = 14,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "%.1f".format(preferences.lineHeight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Theme
            SectionLabel("Theme")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = preferences.theme == theme,
                        onClick = { onPreferencesChanged(preferences.copy(theme = theme)) },
                        label = { Text(theme.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pagination Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Paginated", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.isPaginated,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(isPaginated = it))
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Brightness
            SectionLabel("Brightness")
            Slider(
                value = if (preferences.brightness < 0) 0.5f else preferences.brightness,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(brightness = it))
                },
                valueRange = 0.01f..1.0f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
