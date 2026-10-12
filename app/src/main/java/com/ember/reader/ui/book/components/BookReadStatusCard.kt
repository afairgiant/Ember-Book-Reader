package com.ember.reader.ui.book.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.ui.book.displayName
import com.ember.reader.ui.settings.components.SettingsGroup

/** The book's Grimmory read status as one row; tapping it opens the list of statuses. */
@Composable
fun BookReadStatusCard(
    currentStatus: ReadStatus?,
    onStatusChange: (ReadStatus) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.read_status)) {
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuOpen = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentStatus?.displayName ?: ReadStatus.UNSET.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ReadStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status.displayName) },
                        trailingIcon = {
                            if (status == currentStatus) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            menuOpen = false
                            onStatusChange(status)
                        },
                    )
                }
            }
        }
    }
}
