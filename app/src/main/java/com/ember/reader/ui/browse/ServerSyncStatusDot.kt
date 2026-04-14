package com.ember.reader.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ember.reader.core.sync.SyncStatus

/**
 * Small colored dot indicating the per-server sync health. Renders nothing
 * for healthy statuses ([SyncStatus.Ok], [SyncStatus.Unknown]) to avoid
 * visual noise on the common case.
 *
 * - Red (error) → [SyncStatus.AuthExpired], [SyncStatus.ServerError]
 * - Amber (tertiary) → [SyncStatus.NetworkError]
 */
@Composable
fun ServerSyncStatusDot(status: SyncStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        is SyncStatus.Ok, SyncStatus.Unknown -> return
        is SyncStatus.AuthExpired, is SyncStatus.ServerError -> MaterialTheme.colorScheme.error
        is SyncStatus.NetworkError -> MaterialTheme.colorScheme.tertiary
    }
    DotSwatch(color = color, modifier = modifier)
}

@Composable
private fun DotSwatch(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
