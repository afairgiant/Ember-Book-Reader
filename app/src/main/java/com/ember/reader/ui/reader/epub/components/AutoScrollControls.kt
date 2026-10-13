package com.ember.reader.ui.reader.epub.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.ui.reader.epub.AutoScrollSpeed

/** The pill shown over a page that is moving by itself: slower, the speed, faster, stop. */
@Composable
internal fun AutoScrollControls(
    speed: Float,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        // Opaque: page text showing through makes both it and the label hard to read.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onSlower, enabled = !AutoScrollSpeed.isSlowest(speed)) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.auto_scroll_slower_cd))
            }
            Text(
                text = stringResource(R.string.auto_scroll_speed_label, AutoScrollSpeed.level(speed)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = onFaster, enabled = !AutoScrollSpeed.isFastest(speed)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.auto_scroll_faster_cd))
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.auto_scroll_stop_cd))
            }
        }
    }
}

@Preview
@Composable
private fun AutoScrollControlsPreview() {
    MaterialTheme {
        AutoScrollControls(speed = AutoScrollSpeed.DEFAULT, onSlower = {}, onFaster = {}, onStop = {})
    }
}
