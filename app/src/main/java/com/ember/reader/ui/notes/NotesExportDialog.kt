package com.ember.reader.ui.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ember.reader.R
import com.ember.reader.ui.settings.components.SettingsChoiceChips
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks which form to export [groups] in, then shares the text with another app or saves
 * it as a file wherever the user picks.
 */
@Composable
internal fun NotesExportDialog(groups: List<NotesBookGroup>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { NotesExporter() }
    var format by rememberSaveable { mutableStateOf(NotesExportFormat.MARKDOWN) }

    fun exportText() = exporter.export(groups, format, LocalDate.now())
    fun exportName() = exporter.fileName(groups, format, LocalDate.now())

    // The suggested name carries the ending; the type is left open so one launcher serves both forms.
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = writeText(context, uri, exportText())
            val message = if (saved) R.string.notes_export_saved else R.string.notes_export_failed
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val entries = groups.sumOf { it.marks.size }
    val subject = groups.singleOrNull()?.book?.title ?: "${groups.size} books"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_export_title)) },
        text = {
            Column {
                Text(
                    text = "$subject · ${if (entries == 1) "1 entry" else "$entries entries"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsChoiceChips(
                    options = NotesExportFormat.entries,
                    selected = format,
                    onSelect = { format = it },
                    labelOf = { it.label }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    shareText(context, exportName(), exportText())
                    onDismiss()
                }
            }) { Text(stringResource(R.string.notes_export_share)) }
        },
        dismissButton = {
            TextButton(onClick = { save.launch(exportName()) }) {
                Text(stringResource(R.string.notes_export_save))
            }
        }
    )
}

private suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
    }.isSuccess
}

/**
 * Hands the export to another app. A short one goes as text, which any app can take; a
 * long one would be too big to pass that way and goes as a file.
 */
private suspend fun shareText(context: Context, fileName: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_SUBJECT, fileName) }
    if (text.length <= MAX_SHARED_AS_TEXT) {
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
    } else {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }.resolve(fileName).apply { writeText(text) }
        }
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Text passed between apps shares a small buffer; this stays well inside it. */
private const val MAX_SHARED_AS_TEXT = 100_000
