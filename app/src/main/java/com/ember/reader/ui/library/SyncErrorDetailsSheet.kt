package com.ember.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.reader.core.sync.SyncStatus
import com.ember.reader.ui.theme.EmberTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncErrorDetailsSheet(
    error: SyncStatus.ServerError,
    serverName: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = serverName?.let { "Sync failed · $it" } ?: "Sync failed",
                style = MaterialTheme.typography.titleMedium
            )

            DetailRow(
                label = "Status code",
                value = error.statusCode?.toString() ?: "Unknown"
            )
            DetailRow(
                label = "Message",
                value = error.detail ?: "No details provided by the server."
            )
            DetailRow(
                label = "Last attempt",
                value = formatInstant(error.lastAttemptAt)
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

private fun formatInstant(instant: Instant): String =
    dateFormatter.format(instant.atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SyncErrorDetailsSheetPreview() {
    EmberTheme {
        SyncErrorDetailsSheet(
            error = SyncStatus.ServerError(
                lastAttemptAt = Instant.parse("2026-04-14T10:15:30Z"),
                statusCode = 503,
                detail = "The server is temporarily unavailable."
            ),
            serverName = "Home Grimmory",
            onDismiss = {}
        )
    }
}
