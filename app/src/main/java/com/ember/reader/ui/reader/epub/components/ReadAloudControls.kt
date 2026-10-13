package com.ember.reader.ui.reader.epub.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import com.ember.reader.ui.reader.epub.ReadAloudSpeed

/**
 * The pill shown over a page that is being read aloud: back a sentence, pause or play, on a
 * sentence, slower, the speed, faster, stop.
 */
@Composable
internal fun ReadAloudControls(
    playing: Boolean,
    speed: Double,
    onPrevious: () -> Unit,
    onTogglePlaying: () -> Unit,
    onNext: () -> Unit,
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
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.read_aloud_previous_cd))
            }
            IconButton(onClick = onTogglePlaying) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.read_aloud_pause_cd else R.string.read_aloud_resume_cd
                    )
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.read_aloud_next_cd))
            }
            IconButton(onClick = onSlower, enabled = !ReadAloudSpeed.isSlowest(speed)) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.read_aloud_slower_cd))
            }
            Text(
                text = ReadAloudSpeed.label(speed),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            IconButton(onClick = onFaster, enabled = !ReadAloudSpeed.isFastest(speed)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.read_aloud_faster_cd))
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.read_aloud_stop_cd))
            }
        }
    }
}

@Preview
@Composable
private fun ReadAloudControlsPreview() {
    MaterialTheme {
        ReadAloudControls(
            playing = true,
            speed = ReadAloudSpeed.DEFAULT,
            onPrevious = {},
            onTogglePlaying = {},
            onNext = {},
            onSlower = {},
            onFaster = {},
            onStop = {}
        )
    }
}
