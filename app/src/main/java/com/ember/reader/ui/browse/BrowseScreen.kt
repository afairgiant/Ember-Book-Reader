package com.ember.reader.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.Server
import com.ember.reader.core.sync.SyncStatus
import com.ember.reader.ui.settings.components.SettingsDivider
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsIconTile
import com.ember.reader.ui.settings.components.SettingsNavRow
import com.ember.reader.ui.settings.components.SettingsScreenHeader

@Composable
fun BrowseScreen(
    onOpenLibrary: (Long) -> Unit,
    onOpenHardcover: () -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val syncStatuses by viewModel.syncStatuses.collectAsStateWithLifecycle()
    val hardcoverConnected = viewModel.isHardcoverConnected

    // Auto-navigate if only one server and no Hardcover
    var autoNavigated by remember { mutableStateOf(false) }
    LaunchedEffect(servers, hardcoverConnected) {
        if (servers.size == 1 && !hardcoverConnected && !autoNavigated) {
            autoNavigated = true
            onOpenLibrary(servers.first().id)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsScreenHeader(overline = "Your servers", title = "Browse")

            if (servers.isEmpty() && !hardcoverConnected) {
                BrowseEmptyState()
            } else {
                if (servers.isNotEmpty()) {
                    SettingsGroup(title = "Servers") {
                        servers.forEachIndexed { index, server ->
                            if (index > 0) {
                                SettingsDivider()
                            }
                            BrowseServerRow(
                                server = server,
                                syncStatus = syncStatuses[server.id] ?: SyncStatus.Unknown,
                                onClick = { onOpenLibrary(server.id) }
                            )
                        }
                    }
                }
                if (hardcoverConnected) {
                    SettingsGroup(title = "Services") {
                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = "Hardcover",
                            subtitle = "Book tracking & reading lists",
                            onClick = onOpenHardcover
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CloudQueue,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = "No servers connected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Add a server in Settings to browse books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BrowseServerRow(server: Server, syncStatus: SyncStatus, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile {
            if (server.isGrimmory) {
                Icon(
                    painter = painterResource(R.drawable.ic_grimmory),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (server.isGrimmory) "Grimmory" else "OPDS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ServerSyncStatusDot(status = syncStatus)
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
