package com.ember.reader.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.Server
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsNavRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsHubScreen(
    onEditServer: (Long) -> Unit,
    onAddServer: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenHardcover: () -> Unit,
    onOpenBookdrop: () -> Unit,
    onOpenDevLog: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ember_reader),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Customize your reading experience",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val hasGrimmory = servers.any {
                            it.isGrimmory && viewModel.isGrimmoryLoggedIn(it.id)
                        }
                        if (hasGrimmory) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFF2E7D32),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "Grimmory connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connected Servers
            SettingsGroup(
                title = "Connected Servers",
                subtitle = "Manage your book server connections",
            ) {
                servers.forEachIndexed { index, server ->
                    if (index > 0) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    ServerRow(
                        server = server,
                        isGrimmoryLoggedIn = viewModel.isGrimmoryLoggedIn(server.id),
                        onClick = { onEditServer(server.id) },
                    )
                }
                if (servers.isNotEmpty()) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddServer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.add_server),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App Settings
            SettingsGroup(
                title = "App Settings",
                subtitle = "Appearance, sync, and storage options",
            ) {
                SettingsNavRow(
                    icon = Icons.Default.Palette,
                    title = "Appearance",
                    subtitle = "Theme, keep screen on",
                    onClick = onOpenAppearance,
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingsNavRow(
                    icon = Icons.Default.Sync,
                    title = "Sync",
                    subtitle = "Frequency, highlights, bookmarks",
                    onClick = onOpenSync,
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingsNavRow(
                    icon = Icons.Default.Download,
                    title = "Downloads & Storage",
                    subtitle = "Auto download, cleanup, manage files",
                    onClick = onOpenDownloads,
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingsNavRow(
                    icon = Icons.Default.BarChart,
                    title = "Reading Statistics",
                    subtitle = "History, streaks, and Grimmory stats",
                    onClick = onOpenStats,
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Hardcover",
                    subtitle = "View your reading lists",
                    onClick = onOpenHardcover,
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingsNavRow(
                    icon = Icons.Default.Inbox,
                    title = "Book Drop",
                    subtitle = "Review and import pending books",
                    onClick = onOpenBookdrop,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // About
            SettingsGroup(title = "About") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onOpenDevLog,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Ember Reader",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "v${com.ember.reader.BuildConfig.VERSION_NAME} \u00B7 Long press for dev logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ServerRow(
    server: Server,
    isGrimmoryLoggedIn: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CloudQueue,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(label = "OPDS", active = true)
                if (server.kosyncUsername.isNotBlank()) {
                    StatusDot(label = "Kosync", active = true)
                }
                if (server.isGrimmory) {
                    StatusDot(label = "Grimmory", active = isGrimmoryLoggedIn)
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StatusDot(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
