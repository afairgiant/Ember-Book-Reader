package com.ember.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ember.reader.core.sync.SyncStatus

/**
 * Compact banner shown at the top of [LibraryScreen] whenever the current
 * server's [SyncStatus] is unhealthy. Renders nothing for [SyncStatus.Ok]
 * and [SyncStatus.Unknown] — the caller can unconditionally include it.
 */
@Composable
fun SyncStatusBanner(
    status: SyncStatus,
    serverName: String?,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val presentation = status.bannerPresentation() ?: return
    val serverSuffix = serverName?.let { " · $it" }.orEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = presentation.headline + serverSuffix,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            presentation.supporting?.let {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        if (onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(presentation.actionLabel)
            }
        }
    }
}

private data class BannerPresentation(
    val icon: ImageVector,
    val headline: String,
    val supporting: String?,
    val actionLabel: String
)

private fun SyncStatus.bannerPresentation(): BannerPresentation? = when (this) {
    is SyncStatus.Ok, SyncStatus.Unknown -> null
    is SyncStatus.AuthExpired -> BannerPresentation(
        icon = Icons.Filled.Lock,
        headline = "Please sign in again to sync",
        supporting = "Your session expired and needs to be refreshed.",
        actionLabel = "Sign in"
    )
    is SyncStatus.NetworkError -> BannerPresentation(
        icon = Icons.Filled.WifiOff,
        headline = "Can't reach the server",
        supporting = detail ?: "Progress, bookmarks, and highlights won't sync until you're back online.",
        actionLabel = "Retry"
    )
    is SyncStatus.ServerError -> BannerPresentation(
        icon = Icons.Filled.CloudOff,
        headline = statusCode?.let { "Server error $it" } ?: "Sync failed",
        supporting = detail ?: "The server returned an error. Sync will retry automatically.",
        actionLabel = "Details"
    )
}
