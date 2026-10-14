package com.ember.reader.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.SyncFrequency
import com.ember.reader.ui.settings.components.SettingsDetailRow
import com.ember.reader.ui.settings.components.SettingsDivider
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsScreenHeader
import com.ember.reader.ui.settings.components.SettingsToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val syncFrequency by viewModel.syncFrequency.collectAsStateWithLifecycle()
    val syncNotifications by viewModel.syncNotifications.collectAsStateWithLifecycle()
    val newBookNotifications by viewModel.newBookNotifications.collectAsStateWithLifecycle()
    val syncHighlights by viewModel.syncHighlights.collectAsStateWithLifecycle()
    val syncBookmarks by viewModel.syncBookmarks.collectAsStateWithLifecycle()
    val autoDownloadReading by viewModel.autoDownloadReading.collectAsStateWithLifecycle()
    val autoDownloadReadingEnabled by viewModel.autoDownloadReadingEnabled.collectAsStateWithLifecycle()
    val autoDownloadNextInSeries by viewModel.autoDownloadNextInSeries.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    // The app's outer scaffold has already made room for the system bars.
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsScreenHeader(overline = "Settings", title = "Sync", onNavigateBack = onNavigateBack)
            SettingsGroup(title = "Sync Settings") {
                var showFrequencyMenu by remember { mutableStateOf(false) }

                SettingsDetailRow(
                    icon = Icons.Default.Cloud,
                    title = "Sync Frequency",
                    subtitle = syncFrequency.displayName,
                    modifier = Modifier.clickable { showFrequencyMenu = true }
                ) {
                    DropdownMenu(
                        expanded = showFrequencyMenu,
                        onDismissRequest = { showFrequencyMenu = false }
                    ) {
                        SyncFrequency.entries.forEach { frequency ->
                            DropdownMenuItem(
                                text = { Text(frequency.displayName) },
                                onClick = {
                                    viewModel.updateSyncFrequency(frequency)
                                    showFrequencyMenu = false
                                }
                            )
                        }
                    }
                }

                SettingsDivider()

                SettingsToggleRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.sync_notifications_label),
                    subtitle = stringResource(R.string.sync_notifications_hint),
                    checked = syncNotifications,
                    onCheckedChange = { viewModel.updateSyncNotifications(it) }
                )

                SettingsDivider()

                SettingsToggleRow(
                    icon = Icons.Default.NewReleases,
                    title = stringResource(R.string.new_book_notifications_label),
                    subtitle = stringResource(R.string.new_book_notifications_hint),
                    checked = newBookNotifications,
                    onCheckedChange = { viewModel.updateNewBookNotifications(it) }
                )

                SettingsDivider()

                SettingsToggleRow(
                    icon = Icons.Default.FormatColorFill,
                    title = "Sync highlights",
                    subtitle = "Sync highlights with Grimmory",
                    checked = syncHighlights,
                    onCheckedChange = { viewModel.updateSyncHighlights(it) }
                )

                SettingsDivider()

                SettingsToggleRow(
                    icon = Icons.Default.BookmarkBorder,
                    title = "Sync bookmarks",
                    subtitle = "Sync bookmarks with Grimmory",
                    checked = syncBookmarks,
                    onCheckedChange = { viewModel.updateSyncBookmarks(it) }
                )

                SettingsDivider()

                SettingsToggleRow(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.auto_download_reading),
                    subtitle = if (autoDownloadReadingEnabled) {
                        stringResource(R.string.auto_download_reading_hint)
                    } else {
                        "Disabled \u2014 no connected Grimmory server grants download permission"
                    },
                    checked = autoDownloadReading && autoDownloadReadingEnabled,
                    onCheckedChange = { viewModel.updateAutoDownloadReading(it) },
                    enabled = autoDownloadReadingEnabled
                )

                SettingsDivider()

                // Needs the same permission as the row above, so it is offered on the same terms.
                SettingsToggleRow(
                    icon = Icons.Default.LibraryAdd,
                    title = stringResource(R.string.auto_download_next_in_series),
                    subtitle = stringResource(R.string.auto_download_next_in_series_hint),
                    checked = autoDownloadNextInSeries && autoDownloadReadingEnabled,
                    onCheckedChange = { viewModel.updateAutoDownloadNextInSeries(it) },
                    enabled = autoDownloadReadingEnabled
                )
            }

            Button(
                onClick = viewModel::syncNow,
                enabled = !isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSyncing) {
                    val transition = rememberInfiniteTransition(label = "sync_rotation")
                    val rotation by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = -360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sync_now))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
