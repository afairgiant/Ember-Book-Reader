package com.ember.reader.ui.reader.audiobook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.reader.R
import com.ember.reader.ui.settings.components.SettingsChoiceChips

/** The skip lengths offered, in seconds. */
internal val SKIP_SECONDS = listOf(10, 15, 30, 60)

/**
 * The icon for a skip of [seconds], back or [forward]. The usual lengths have an icon of
 * their own; the others are the plain arrow with the number in it.
 */
@Composable
internal fun SkipIcon(seconds: Int, forward: Boolean, contentDescription: String, modifier: Modifier = Modifier) {
    val ready = when {
        seconds == 10 && forward -> Icons.Default.Forward10
        seconds == 30 && forward -> Icons.Default.Forward30
        seconds == 10 -> Icons.Default.Replay10
        seconds == 30 -> Icons.Default.Replay30
        else -> null
    }
    if (ready != null) {
        Icon(ready, contentDescription = contentDescription, modifier = modifier)
        return
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Icon(
            Icons.Default.Replay,
            contentDescription = contentDescription,
            // The arrow turns the other way for a skip forward.
            modifier = Modifier.matchParentSize().graphicsLayer { scaleX = if (forward) -1f else 1f }
        )
        Text(
            text = seconds.toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** Sets or turns off the sleep timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    timer: SleepTimer,
    timeLeftMs: Long?,
    onSetMinutes: (Int) -> Unit,
    onSetEndOfChapter: () -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opened in full: half open, the last choices sit under the navigation bar.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.sleep_timer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (timer != SleepTimer.Off) {
                Text(
                    text = when (timer) {
                        is SleepTimer.EndOfChapter -> stringResource(R.string.sleep_timer_stops_end_of_chapter)
                        else -> stringResource(R.string.sleep_timer_stops_in, formatTimeLeft(timeLeftMs ?: 0))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SheetRow(stringResource(R.string.sleep_timer_turn_off)) {
                    onTurnOff()
                    onDismiss()
                }
            }
            SleepTimer.MINUTES.forEach { minutes ->
                SheetRow(pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)) {
                    onSetMinutes(minutes)
                    onDismiss()
                }
            }
            SheetRow(stringResource(R.string.sleep_timer_end_of_chapter)) {
                onSetEndOfChapter()
                onDismiss()
            }
        }
    }
}

/** How far the skip buttons jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkipLengthSheet(
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onSkipBackChange: (Int) -> Unit,
    onSkipForwardChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opened in full: half open, the last choices sit under the navigation bar.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val context = LocalContext.current
        val labelOf = { seconds: Int -> context.getString(R.string.audiobook_skip_seconds, seconds) }
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.audiobook_skip_back),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SettingsChoiceChips(
                options = SKIP_SECONDS,
                selected = skipBackSeconds,
                onSelect = onSkipBackChange,
                labelOf = labelOf
            )
            Text(
                text = stringResource(R.string.audiobook_skip_forward),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            SettingsChoiceChips(
                options = SKIP_SECONDS,
                selected = skipForwardSeconds,
                onSelect = onSkipForwardChange,
                labelOf = labelOf
            )
        }
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}
