package com.ember.reader.ui.reader.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.reader.R
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.ui.common.SectionLabel

enum class ActiveMarginSlider { Side, Top, Bottom }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPreferencesSheet(
    preferences: ReaderPreferences,
    onPreferencesChanged: (ReaderPreferences) -> Unit,
    onDismiss: () -> Unit,
    isPdf: Boolean = false,
    hasOverride: Boolean = false,
    onResetToDefaults: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeMarginSlider by remember { mutableStateOf<ActiveMarginSlider?>(null) }
    val previewingMargin = activeMarginSlider != null
    val defaultContainerColor = BottomSheetDefaults.ContainerColor
    val containerColor by animateColorAsState(
        targetValue = if (previewingMargin) Color.Transparent else defaultContainerColor,
        label = "sheet-container",
    )
    val scrimColor by animateColorAsState(
        targetValue = if (previewingMargin) Color.Transparent else BottomSheetDefaults.ScrimColor,
        label = "sheet-scrim",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (previewingMargin) 0f else 1f,
        label = "sheet-content-alpha",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = scrimColor,
        containerColor = containerColor,
        dragHandle = if (previewingMargin) {
            null
        } else {
            { BottomSheetDefaults.DragHandle() }
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .alpha(contentAlpha)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.reader_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Override status header — tells the user whether tweaks here apply
            // only to this book or to global defaults, with a one-tap reset.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (hasOverride) {
                        "Custom settings for this book"
                    } else {
                        "Using global defaults"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasOverride && onResetToDefaults != null) {
                    TextButton(onClick = onResetToDefaults) {
                        Text("Reset to defaults")
                    }
                }
            }

            ReaderPreferencesContent(
                preferences = preferences,
                onPreferencesChanged = onPreferencesChanged,
                isPdf = isPdf,
                onMarginPreviewChanged = { activeMarginSlider = it },
            )
        }

        activeMarginSlider?.let { active ->
            MarginPreviewOverlay(
                active = active,
                preferences = preferences,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp),
            )
        }
        }
    }
}

@Composable
private fun MarginPreviewOverlay(
    active: ActiveMarginSlider,
    preferences: ReaderPreferences,
    modifier: Modifier = Modifier,
) {
    val label: String
    val value: Float
    val valueRange: ClosedFloatingPointRange<Float>
    val valueText: String
    when (active) {
        ActiveMarginSlider.Side -> {
            label = stringResource(R.string.side_margins_section)
            value = preferences.pageMargins
            valueRange = 0.5f..2.5f
            valueText = "%.2f".format(preferences.pageMargins)
        }
        ActiveMarginSlider.Top -> {
            label = stringResource(R.string.top_margin_section)
            value = preferences.marginTop.toFloat()
            valueRange = 0f..48f
            valueText = "${preferences.marginTop} dp"
        }
        ActiveMarginSlider.Bottom -> {
            label = stringResource(R.string.bottom_margin_section)
            value = preferences.marginBottom.toFloat()
            valueRange = 0f..48f
            valueText = "${preferences.marginBottom} dp"
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = {},
            valueRange = valueRange,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Modifier.observeTouchPresence(onChanged: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onChanged(true)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.all { !it.pressed }) break
                }
            } finally {
                onChanged(false)
            }
        }
    }

/**
 * The form body of the reader preferences UI, decoupled from any container.
 * Used by both the in-book [ReaderPreferencesSheet] and the global
 * Appearance > Reader Defaults screen so the two stay in sync automatically.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderPreferencesContent(
    preferences: ReaderPreferences,
    onPreferencesChanged: (ReaderPreferences) -> Unit,
    isPdf: Boolean = false,
    onMarginPreviewChanged: (ActiveMarginSlider?) -> Unit = {},
) {
    var sideTouched by remember { mutableStateOf(false) }
    var topTouched by remember { mutableStateOf(false) }
    var bottomTouched by remember { mutableStateOf(false) }
    val activeSlider: ActiveMarginSlider? = when {
        sideTouched -> ActiveMarginSlider.Side
        topTouched -> ActiveMarginSlider.Top
        bottomTouched -> ActiveMarginSlider.Bottom
        else -> null
    }
    LaunchedEffect(activeSlider) {
        onMarginPreviewChanged(activeSlider)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // PDF-specific controls
        if (isPdf) {
            SectionLabel("Page Fit")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.ember.reader.core.model.PdfFitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.pdfFitMode == mode,
                        onClick = { onPreferencesChanged(preferences.copy(pdfFitMode = mode)) },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Page Spacing")
            Slider(
                value = preferences.pdfPageSpacing,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(pdfPageSpacing = it))
                },
                valueRange = 0f..30f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${preferences.pdfPageSpacing.toInt()} pt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // EPUB-specific controls
        if (!isPdf) {
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

            SectionLabel(stringResource(R.string.side_margins_section))
            Box(modifier = Modifier.observeTouchPresence { sideTouched = it }) {
                Slider(
                    value = preferences.pageMargins,
                    onValueChange = {
                        onPreferencesChanged(preferences.copy(pageMargins = it))
                    },
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "%.2f".format(preferences.pageMargins),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.top_margin_section))
            Box(modifier = Modifier.observeTouchPresence { topTouched = it }) {
                Slider(
                    value = preferences.marginTop.toFloat(),
                    onValueChange = {
                        onPreferencesChanged(preferences.copy(marginTop = it.toInt()))
                    },
                    valueRange = 0f..48f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "${preferences.marginTop} dp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.bottom_margin_section))
            Box(modifier = Modifier.observeTouchPresence { bottomTouched = it }) {
                Slider(
                    value = preferences.marginBottom.toFloat(),
                    onValueChange = {
                        onPreferencesChanged(preferences.copy(marginBottom = it.toInt()))
                    },
                    valueRange = 0f..48f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "${preferences.marginBottom} dp",
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
        }

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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Volume buttons turn pages", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = preferences.volumePageTurn,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(volumePageTurn = it))
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Show progress percentage", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = preferences.showProgressIndicator,
                onCheckedChange = {
                    onPreferencesChanged(preferences.copy(showProgressIndicator = it))
                }
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
