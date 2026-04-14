package com.ember.reader.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Permission state badge used across the server-add flow, the Settings server
 * list, and anywhere else we surface Grimmory capability flags. `null` means
 * the server hasn't reported a value yet (rendered muted, not red).
 */
@Composable
fun PermissionChip(label: String, granted: Boolean?) {
    val color: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when (granted) {
        true -> {
            color = Color(0xFF4CAF50)
            icon = Icons.Default.Check
        }
        false -> {
            color = MaterialTheme.colorScheme.error
            icon = Icons.Default.Close
        }
        null -> {
            color = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Default.Close
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
