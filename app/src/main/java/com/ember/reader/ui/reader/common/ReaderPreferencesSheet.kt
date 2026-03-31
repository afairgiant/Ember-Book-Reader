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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ember.reader.R
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.ui.common.SectionLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderPreferencesSheet(
    preferences: ReaderPreferences,
    onPreferencesChanged: (ReaderPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.reader_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SectionLabel(stringResource(R.string.font_section))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FontFamily.entries.forEach { font ->
                    FilterChip(
                        selected = preferences.fontFamily == font,
                        onClick = { onPreferencesChanged(preferences.copy(fontFamily = font)) },
                        label = { Text(font.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.font_size_section))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        val newSize = (preferences.fontSize - 1f).coerceAtLeast(12f)
                        onPreferencesChanged(preferences.copy(fontSize = newSize))
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease))
                }
                Text(
                    text = "${preferences.fontSize.toInt()}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = {
                        val newSize = (preferences.fontSize + 1f).coerceAtMost(32f)
                        onPreferencesChanged(preferences.copy(fontSize = newSize))
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.line_height_section))
            Slider(
                value = preferences.lineHeight,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(lineHeight = it))
                },
                valueRange = 1.0f..2.5f,
                steps = 14,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "%.1f".format(preferences.lineHeight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.text_align_section))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.ember.reader.core.model.TextAlign.entries.forEach { align ->
                    FilterChip(
                        selected = preferences.textAlign == align,
                        onClick = { onPreferencesChanged(preferences.copy(textAlign = align)) },
                        label = { Text(align.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.page_margins_section))
            Slider(
                value = preferences.pageMargins,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(pageMargins = it))
                },
                valueRange = 0.5f..2.5f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "%.1f".format(preferences.pageMargins),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.publisher_styles), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.publisherStyles,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(publisherStyles = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.hyphenation), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.hyphenate,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(hyphenate = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.theme_section))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                ReaderTheme.entries.forEach { theme ->
                    val bgColor = if (theme == ReaderTheme.SYSTEM) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color(theme.backgroundColor)
                    }
                    val fgColor = if (theme == ReaderTheme.SYSTEM) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(theme.foregroundColor)
                    }
                    val isSelected = preferences.theme == theme
                    val borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray.copy(alpha = 0.3f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = borderColor,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onPreferencesChanged(preferences.copy(theme = theme))
                                }
                        ) {
                            Text(
                                text = "Aa",
                                color = fgColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = ComposeTextAlign.Center
                            )
                        }
                        Text(
                            text = theme.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.paginated_mode), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.isPaginated,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(isPaginated = it))
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Volume buttons turn pages", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.volumePageTurn,
                    onCheckedChange = {
                        onPreferencesChanged(preferences.copy(volumePageTurn = it))
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.orientation_section))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.ember.reader.core.model.OrientationLock.entries.forEach { lock ->
                    FilterChip(
                        selected = preferences.orientationLock == lock,
                        onClick = { onPreferencesChanged(preferences.copy(orientationLock = lock)) },
                        label = { Text(lock.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.tap_zones_section))
            TapZoneSelector("Top tap", preferences.topTapZone) {
                onPreferencesChanged(preferences.copy(topTapZone = it))
            }
            TapZoneSelector("Left tap", preferences.leftTapZone) {
                onPreferencesChanged(preferences.copy(leftTapZone = it))
            }
            TapZoneSelector("Center tap", preferences.centerTapZone) {
                onPreferencesChanged(preferences.copy(centerTapZone = it))
            }
            TapZoneSelector("Right tap", preferences.rightTapZone) {
                onPreferencesChanged(preferences.copy(rightTapZone = it))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Top zone height: ${(preferences.topZoneHeight * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = preferences.topZoneHeight,
                onValueChange = { onPreferencesChanged(preferences.copy(topZoneHeight = it)) },
                valueRange = 0.05f..0.30f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Left/Right zone width: ${(preferences.leftZoneWidth * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = preferences.leftZoneWidth,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(leftZoneWidth = it, rightZoneWidth = it))
                },
                valueRange = 0.15f..0.45f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.brightness_section))
            Slider(
                value = if (preferences.brightness < 0) 0.5f else preferences.brightness,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(brightness = it))
                },
                valueRange = 0.01f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TapZoneSelector(
    label: String,
    selected: com.ember.reader.core.model.TapZoneBehavior,
    onChanged: (com.ember.reader.core.model.TapZoneBehavior) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            FilterChip(
                selected = true,
                onClick = { expanded = true },
                label = { Text(selected.displayName) }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                com.ember.reader.core.model.TapZoneBehavior.entries.forEach { behavior ->
                    DropdownMenuItem(
                        text = { Text(behavior.displayName) },
                        onClick = {
                            onChanged(behavior)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
