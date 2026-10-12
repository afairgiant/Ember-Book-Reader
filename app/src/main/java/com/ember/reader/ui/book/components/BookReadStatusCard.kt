package com.ember.reader.ui.book.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.ui.book.displayName
import com.ember.reader.ui.settings.components.SettingsGroup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookReadStatusCard(
    currentStatus: ReadStatus?,
    onStatusChange: (ReadStatus) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.read_status)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReadStatus.entries.forEach { status ->
                    FilterChip(
                        selected = currentStatus == status,
                        onClick = { onStatusChange(status) },
                        label = { Text(status.displayName) },
                    )
                }
            }
        }
    }
}
