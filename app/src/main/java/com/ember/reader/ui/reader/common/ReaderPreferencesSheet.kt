package com.ember.reader.ui.reader.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.ui.common.SectionLabel

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

            SectionLabel("Theme")
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                ReaderTheme.entries.forEach { theme ->
                    val bgColor = when (theme) {
                        ReaderTheme.LIGHT -> Color.White
                        ReaderTheme.DARK -> Color(0xFF2A2A2A)
                        ReaderTheme.SEPIA -> Color(0xFFF5E6D0)
                        ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.surface
                    }
                    val borderColor = if (preferences.theme == theme) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray.copy(alpha = 0.3f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(
                                    width = if (preferences.theme == theme) 3.dp else 1.dp,
                                    color = borderColor,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    onPreferencesChanged(preferences.copy(theme = theme))
                                },
                        )
                        Text(
                            text = theme.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Paginated Mode", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.isPaginated,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(isPaginated = it))
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Orientation")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.ember.reader.core.model.OrientationLock.entries.forEach { lock ->
                    FilterChip(
                        selected = preferences.orientationLock == lock,
                        onClick = { onPreferencesChanged(preferences.copy(orientationLock = lock)) },
                        label = { Text(lock.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
