package com.ember.reader.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ember.reader.R

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

/** Converts raw exception messages to user-friendly error strings. */
fun friendlyErrorMessage(error: Throwable): String = when {
    error.message?.contains("Unable to resolve host") == true -> "Can't reach server — check your internet connection"
    error.message?.contains("timeout") == true -> "Connection timed out — server may be down"
    error.message?.contains("Connection refused") == true -> "Connection refused — server may be offline"
    error.message?.contains("SSL") == true || error.message?.contains("Certificate") == true ->
        "SSL/certificate error — check your server's HTTPS setup"
    error.message?.contains("401") == true -> "Authentication failed — check your credentials"
    error.message?.contains("403") == true -> "Access denied — you may not have permission"
    error.message?.contains("404") == true -> "Not found — the resource doesn't exist on this server"
    error.message?.contains("500") == true -> "Server error — try again later"
    error is java.net.UnknownHostException -> "Can't reach server — check your internet connection"
    error is java.net.ConnectException -> "Can't connect to server"
    error is java.net.SocketTimeoutException -> "Connection timed out"
    else -> error.message ?: "An unexpected error occurred"
}

val BookCoverPlaceholderColors = listOf(
    Color(0xFFFFE0D0), Color(0xFFE8D5C8), Color(0xFFFFF0E0),
    Color(0xFFD4E8D0), Color(0xFFD8D8E8), Color(0xFFE8D0D8)
)

fun bookCoverColorIndex(title: String): Int {
    val index = title.hashCode().mod(BookCoverPlaceholderColors.size)
    return if (index < 0) index + BookCoverPlaceholderColors.size else index
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
