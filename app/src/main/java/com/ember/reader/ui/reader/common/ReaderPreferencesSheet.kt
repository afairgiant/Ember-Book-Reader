package com.ember.reader.ui.reader.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.ember.reader.core.fonts.CustomFont
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.OrientationLock
import com.ember.reader.core.model.PdfFitMode
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.core.model.TapZoneBehavior
import com.ember.reader.core.model.TextAlign

enum class ActiveMarginSlider { Side, Top, Bottom }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPreferencesSheet(
    preferences: ReaderPreferences,
    onPreferencesChanged: (ReaderPreferences) -> Unit,
    onDismiss: () -> Unit,
    isPdf: Boolean = false,
    customFonts: List<CustomFont> = emptyList(),
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
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            // Override status header — tells the user whether tweaks here apply
            // only to this book or to global defaults, with a one-tap reset.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (hasOverride) {
                        "Custom settings for this book"
                    } else {
                        "Using global defaults"
                    },
                    style = MaterialTheme.typography.bodyMedium,
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
                customFonts = customFonts,
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
    customFonts: List<CustomFont> = emptyList(),
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
        if (isPdf) {
            ReaderSettingsSection("Page") {
                ReaderChoicePills(
                    options = PdfFitMode.entries,
                    isSelected = { preferences.pdfFitMode == it },
                    onSelect = { onPreferencesChanged(preferences.copy(pdfFitMode = it)) },
                    labelOf = { it.displayName }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ReaderValueSlider(
                    label = "Page spacing",
                    valueText = "${preferences.pdfPageSpacing.toInt()} pt",
                    value = preferences.pdfPageSpacing,
                    onValueChange = { onPreferencesChanged(preferences.copy(pdfPageSpacing = it)) },
                    valueRange = 0f..30f,
                    steps = 5
                )
            }
        }

        if (!isPdf) {
            ReaderSettingsSection(stringResource(R.string.font_section)) {
                // A selected custom font that has since been removed selects nothing here;
                // the reader falls back to the built-in family.
                val activeCustomFont = preferences.customFont?.takeIf { name -> customFonts.any { it.name == name } }
                ReaderChoicePills(
                    options = FontFamily.entries.map { FontChoice.BuiltIn(it) } +
                        customFonts.map { FontChoice.Custom(it.name) },
                    isSelected = { choice ->
                        when (choice) {
                            is FontChoice.BuiltIn -> activeCustomFont == null && preferences.fontFamily == choice.family
                            is FontChoice.Custom -> activeCustomFont == choice.name
                        }
                    },
                    onSelect = { choice ->
                        onPreferencesChanged(
                            when (choice) {
                                is FontChoice.BuiltIn -> preferences.copy(fontFamily = choice.family, customFont = null)
                                is FontChoice.Custom -> preferences.copy(customFont = choice.name)
                            }
                        )
                    },
                    labelOf = { it.label }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.font_size_section),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
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
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = ComposeTextAlign.Center,
                        modifier = Modifier.width(36.dp)
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
            }
        }

        // A PDF is drawn as it is: the theme, like the fonts and margins, does not reach it.
        if (!isPdf) {
            ReaderSettingsSection(stringResource(R.string.theme_section)) {
                ThemeSwatches(
                    selected = preferences.theme,
                    onSelect = { onPreferencesChanged(preferences.copy(theme = it)) }
                )
            }

            ReaderSettingsSection("Text layout") {
                ReaderToggleRow(
                    label = stringResource(R.string.publisher_styles),
                    checked = preferences.publisherStyles,
                    onCheckedChange = { onPreferencesChanged(preferences.copy(publisherStyles = it)) }
                )

                // Readium applies everything in here only while the book's own styles are off.
                AnimatedVisibility(
                    visible = !preferences.publisherStyles,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        ReaderToggleRow(
                            label = stringResource(R.string.hyphenation),
                            checked = preferences.hyphenate,
                            onCheckedChange = { onPreferencesChanged(preferences.copy(hyphenate = it)) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.text_align_section),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ReaderChoicePills(
                            options = TextAlign.entries,
                            isSelected = { preferences.textAlign == it },
                            onSelect = { onPreferencesChanged(preferences.copy(textAlign = it)) },
                            labelOf = { it.displayName }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ReaderValueSlider(
                            label = stringResource(R.string.line_height_section),
                            valueText = "%.1f".format(preferences.lineHeight),
                            value = preferences.lineHeight,
                            onValueChange = { onPreferencesChanged(preferences.copy(lineHeight = it)) },
                            valueRange = 1.0f..2.5f,
                            steps = 14
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !preferences.publisherStyles,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ReaderSettingsSection("Spacing") {
                    ReaderSpacingControls(
                        preferences = preferences,
                        onPreferencesChanged = onPreferencesChanged
                    )
                }
            }

            ReaderSettingsSection(stringResource(R.string.margins_section)) {
                MarginSlider(
                    label = stringResource(R.string.side_margin_label),
                    value = preferences.pageMargins,
                    valueRange = 0.5f..2.5f,
                    onTouchChanged = { sideTouched = it },
                    onValueChange = { onPreferencesChanged(preferences.copy(pageMargins = it)) }
                )
                MarginSlider(
                    label = stringResource(R.string.top_margin_label),
                    value = preferences.marginTop.toFloat(),
                    valueRange = 0f..48f,
                    onTouchChanged = { topTouched = it },
                    onValueChange = { onPreferencesChanged(preferences.copy(marginTop = it.toInt())) }
                )
                MarginSlider(
                    label = stringResource(R.string.bottom_margin_label),
                    value = preferences.marginBottom.toFloat(),
                    valueRange = 0f..48f,
                    onTouchChanged = { bottomTouched = it },
                    onValueChange = { onPreferencesChanged(preferences.copy(marginBottom = it.toInt())) }
                )
            }
        }

        ReaderSettingsSection("Reading") {
            if (!isPdf) {
                ReaderToggleRow(
                    label = stringResource(R.string.paginated_mode),
                    checked = preferences.isPaginated,
                    onCheckedChange = { onPreferencesChanged(preferences.copy(isPaginated = it)) }
                )
            }
            ReaderToggleRow(
                label = "Volume buttons turn pages",
                checked = preferences.volumePageTurn,
                onCheckedChange = { onPreferencesChanged(preferences.copy(volumePageTurn = it)) }
            )
            ReaderToggleRow(
                label = "Show progress percentage",
                checked = preferences.showProgressIndicator,
                onCheckedChange = { onPreferencesChanged(preferences.copy(showProgressIndicator = it)) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.orientation_section),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ReaderChoicePills(
                options = OrientationLock.entries,
                isSelected = { preferences.orientationLock == it },
                onSelect = { onPreferencesChanged(preferences.copy(orientationLock = it)) },
                labelOf = { it.displayName }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ReaderValueSlider(
                label = stringResource(R.string.brightness_section),
                valueText = if (preferences.brightness < 0) "System" else "${(preferences.brightness * 100).toInt()}%",
                value = if (preferences.brightness < 0) 0.5f else preferences.brightness,
                onValueChange = { onPreferencesChanged(preferences.copy(brightness = it)) },
                valueRange = 0.01f..1.0f
            )
            // Offered only once a level is held, as the way back to the device's own.
            if (preferences.brightness >= 0) {
                TextButton(onClick = { onPreferencesChanged(preferences.copy(brightness = -1f)) }) {
                    Text(stringResource(R.string.brightness_use_system))
                }
            }
            ReaderToggleRow(
                label = stringResource(R.string.brightness_edge_swipe),
                checked = preferences.edgeBrightnessSwipe,
                onCheckedChange = { onPreferencesChanged(preferences.copy(edgeBrightnessSwipe = it)) }
            )
        }

        // In a PDF a tap only shows or hides the bars.
        if (!isPdf) ReaderSettingsSection(stringResource(R.string.tap_zones_section)) {
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
            ReaderValueSlider(
                label = "Top zone height",
                valueText = "${(preferences.topZoneHeight * 100).toInt()}%",
                value = preferences.topZoneHeight,
                onValueChange = { onPreferencesChanged(preferences.copy(topZoneHeight = it)) },
                valueRange = 0.05f..0.30f
            )
            ReaderValueSlider(
                label = "Left and right zone width",
                valueText = "${(preferences.leftZoneWidth * 100).toInt()}%",
                value = preferences.leftZoneWidth,
                onValueChange = {
                    onPreferencesChanged(preferences.copy(leftZoneWidth = it, rightZoneWidth = it))
                },
                valueRange = 0.15f..0.45f
            )
        }
    }
}

/** A font the reader can be set to: one of the built-in families, or one the user added. */
private sealed interface FontChoice {
    val label: String

    data class BuiltIn(val family: FontFamily) : FontChoice {
        override val label: String get() = family.displayName
    }

    data class Custom(val name: String) : FontChoice {
        override val label: String get() = name
    }
}

/** A margin's slider. Touching it tells the sheet to fade out so the page shows through. */
@Composable
private fun MarginSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onTouchChanged: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .observeTouchPresence(onTouchChanged)
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The page colour themes as "Aa" swatches; the chosen one has a ring in the accent colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSwatches(selected: ReaderTheme, onSelect: (ReaderTheme) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
            val isSelected = selected == theme
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                        .clickable { onSelect(theme) }
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
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TapZoneSelector(
    label: String,
    selected: TapZoneBehavior,
    onChanged: (TapZoneBehavior) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selected.displayName, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TapZoneBehavior.entries.forEach { behavior ->
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
