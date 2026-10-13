package com.ember.reader.ui.reader.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ember.reader.core.model.ReaderPreferences
import kotlin.math.roundToInt

/** A spacing setting: its slider's range, and what its untouched state is. */
internal data class SpacingSetting(
    val label: String,
    val max: Float,
    val step: Float,
    /** The value the setting has before the reader touches it; null means "the book decides". */
    val default: Float?
) {
    /** Slider stops between the two ends. */
    val steps: Int get() = (max / step).roundToInt() - 1

    /** How [value] reads beside the slider. */
    fun valueLabel(value: Float?): String = when {
        value == null -> "Book default"
        value == 0f -> if (default == null) "None" else "Normal"
        else -> "%.1f".format(value)
    }

    /** [raw] moved to the nearest slider stop, so a drag never stores 0.30000001. */
    fun snap(raw: Float): Float = ((raw / step).roundToInt() * step).coerceIn(0f, max)
}

// Ranges and steps are the ones Readium's own preferences editor uses, in rem.
internal val WordSpacing = SpacingSetting("Word spacing", max = 1f, step = 0.1f, default = 0f)
internal val LetterSpacing = SpacingSetting("Letter spacing", max = 1f, step = 0.1f, default = 0f)
internal val ParagraphSpacing = SpacingSetting("Paragraph spacing", max = 2f, step = 0.1f, default = null)
internal val ParagraphIndent = SpacingSetting("First-line indent", max = 3f, step = 0.2f, default = null)

/**
 * Sliders for word, letter and paragraph spacing and first-line indent. Readium applies these
 * only with publisher styles off, so the caller shows them in that state alone.
 */
@Composable
fun ReaderSpacingControls(preferences: ReaderPreferences, onPreferencesChanged: (ReaderPreferences) -> Unit) {
    Column {
        SpacingSlider(WordSpacing, preferences.wordSpacing) {
            onPreferencesChanged(preferences.copy(wordSpacing = it ?: 0f))
        }
        SpacingSlider(LetterSpacing, preferences.letterSpacing) {
            onPreferencesChanged(preferences.copy(letterSpacing = it ?: 0f))
        }
        SpacingSlider(ParagraphSpacing, preferences.paragraphSpacing) {
            onPreferencesChanged(preferences.copy(paragraphSpacing = it))
        }
        SpacingSlider(ParagraphIndent, preferences.paragraphIndent) {
            onPreferencesChanged(preferences.copy(paragraphIndent = it))
        }
    }
}

/** One setting: its name and current value, a reset once it has been changed, and the slider. */
@Composable
private fun SpacingSlider(setting: SpacingSetting, value: Float?, onChange: (Float?) -> Unit) {
    // As tall as the Reset button whether or not it is showing, so the slider under the
    // finger does not jump down the moment a value first changes.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(setting.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = setting.valueLabel(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value != setting.default) {
            TextButton(onClick = { onChange(setting.default) }) { Text("Reset") }
        }
    }
    Slider(
        value = value ?: 0f,
        onValueChange = { onChange(setting.snap(it)) },
        valueRange = 0f..setting.max,
        steps = setting.steps,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = setting.label }
    )
}
