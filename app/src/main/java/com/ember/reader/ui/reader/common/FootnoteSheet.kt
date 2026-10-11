package com.ember.reader.ui.reader.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.reader.R

/**
 * Shows a footnote or endnote over the page it was opened from.
 *
 * @param noteHtml the note, already reduced to basic formatting.
 * @param onGoToNote follows the link the usual way, to where the note sits in the book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootnoteSheet(
    noteHtml: String,
    onGoToNote: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        FootnoteContent(noteHtml = noteHtml, onGoToNote = onGoToNote)
    }
}

@Composable
private fun FootnoteContent(noteHtml: String, onGoToNote: () -> Unit) {
    val note = remember(noteHtml) { AnnotatedString.fromHtml(noteHtml) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.footnote_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        )
        TextButton(
            onClick = onGoToNote,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.footnote_go_to_note))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FootnoteContentPreview() {
    MaterialTheme {
        FootnoteContent(
            noteHtml = "<sup>3</sup> A <i>hansom cab</i> was a two-wheeled carriage for hire, " +
                "common in London until the 1900s.",
            onGoToNote = {}
        )
    }
}
