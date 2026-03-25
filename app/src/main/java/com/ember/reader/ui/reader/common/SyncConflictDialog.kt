package com.ember.reader.ui.reader.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun SyncConflictDialog(
    conflict: SyncConflict,
    onAcceptRemote: () -> Unit,
    onKeepLocal: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepLocal,
        title = { Text("Sync Conflict") },
        text = {
            Text(
                "A newer reading position was found on the server " +
                    "(${(conflict.remotePercentage * 100).toInt()}%). " +
                    "Your local position is at ${(conflict.localPercentage * 100).toInt()}%. " +
                    "Would you like to jump to the server position?",
            )
        },
        confirmButton = {
            TextButton(onClick = onAcceptRemote) {
                Text("Jump to server position")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepLocal) {
                Text("Keep local position")
            }
        },
    )
}
