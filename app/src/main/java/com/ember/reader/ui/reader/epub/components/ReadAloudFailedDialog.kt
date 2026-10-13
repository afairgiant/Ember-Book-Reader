package com.ember.reader.ui.reader.epub.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ember.reader.R
import com.ember.reader.ui.reader.epub.ReadAloudFailure

/** Says why the book cannot be read aloud, and offers the voice installer when that would help. */
@Composable
internal fun ReadAloudFailedDialog(reason: ReadAloudFailure, onInstallVoice: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.read_aloud_failed_title)) },
        text = {
            Text(
                stringResource(
                    when (reason) {
                        ReadAloudFailure.NO_TEXT -> R.string.read_aloud_failed_no_text
                        ReadAloudFailure.NO_ENGINE -> R.string.read_aloud_failed_no_engine
                        ReadAloudFailure.VOICE_MISSING -> R.string.read_aloud_failed_voice_missing
                        ReadAloudFailure.OTHER -> R.string.read_aloud_failed_other
                    }
                )
            )
        },
        confirmButton = {
            if (reason == ReadAloudFailure.VOICE_MISSING) {
                TextButton(onClick = onInstallVoice) { Text(stringResource(R.string.read_aloud_install_voice)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (reason == ReadAloudFailure.VOICE_MISSING) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Preview
@Composable
private fun ReadAloudFailedDialogPreview() {
    ReadAloudFailedDialog(reason = ReadAloudFailure.VOICE_MISSING, onInstallVoice = {}, onDismiss = {})
}
