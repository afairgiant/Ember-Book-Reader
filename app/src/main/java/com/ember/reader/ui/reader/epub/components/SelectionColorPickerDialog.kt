package com.ember.reader.ui.reader.epub.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ember.reader.R
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.ui.reader.common.HighlightColorPicker

/**
 * Color picker dialog shown after the user selects text and taps "Highlight"
 * in the selection action menu.
 */
@Composable
fun SelectionColorPickerDialog(
    onColorSelected: (HighlightColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.highlight_action)) },
        text = {
            HighlightColorPicker(
                selectedColor = HighlightColor.YELLOW,
                onColorSelected = onColorSelected,
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
